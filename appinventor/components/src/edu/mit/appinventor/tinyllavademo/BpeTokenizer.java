// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2026 MIT, All rights reserved
// Released under the Apache License, Version 2.0

package edu.mit.appinventor.tinyllavademo;

import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.Form;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Byte-level BPE tokenizer compatible with Qwen2 / tiktoken encoding.
 *
 * Loaded from two asset files:
 *   vocab.json   — maps BPE token string → integer id
 *   merges.txt   — BPE merge rules, one per line "a b" (skip first line)
 *
 * Special tokens are hardcoded to match Qwen2's tokenizer_config.json:
 *   <|endoftext|> → 151643
 *   <|im_start|>  → 151644
 *   <|im_end|>    → 151645
 */
class BpeTokenizer {

  // special tokens
  private final Map<String, Integer> specialTokens = new HashMap<>();
  private final Map<Integer, String> specialTokensReverse = new HashMap<>();
  public final int eosTokenId = 151643;

  // bytes-to-unicode table
  private final Map<Integer, String> bytesToUnicode = new HashMap<>();
  private final Map<Character, Integer> unicodeToBytes = new HashMap<>();

  // vocabulary and merges
  private final Map<String, Integer> vocab = new HashMap<>();
  private final Map<TokenPair, Integer> mergeRanks = new HashMap<>();

  // Reverse vocab: id → token string (lazily populated in decodeSingle)
  private String[] reverseVocab = null;

  // pre-tokenisation pattern (GPT-2 style)
  private static final Pattern PRETOKEN_PATTERN = Pattern.compile(
      "'s|'t|'re|'ve|'m|'ll|'d| ?[A-Za-z]+| ?[0-9]+| ?[^\\s\\w]+|\\s+(?!\\S)|\\s+");

  private final Pattern specialSplitPattern;

  BpeTokenizer(Form form, Component component) throws Exception {
    specialTokens.put("<|endoftext|>", 151643);
    specialTokens.put("<|im_start|>",  151644);
    specialTokens.put("<|im_end|>",    151645);
    for (Map.Entry<String, Integer> e : specialTokens.entrySet()) {
      specialTokensReverse.put(e.getValue(), e.getKey());
    }

    // bytes-to-unicode table (same as GPT-2 / tiktoken)
    List<Integer> base = new ArrayList<>();
    for (int c = '!'; c <= '~'; c++)  base.add(c);
    for (int c = 0xA1; c <= 0xAC; c++) base.add(c);
    for (int c = 0xAE; c <= 0xFF; c++) base.add(c);
    for (int b : base) {
      bytesToUnicode.put(b, String.valueOf((char) b));
    }
    int n = 0;
    for (int b = 0; b <= 255; b++) {
      if (!bytesToUnicode.containsKey(b)) {
        bytesToUnicode.put(b, String.valueOf((char) (256 + n)));
        n++;
      }
    }
    for (Map.Entry<Integer, String> e : bytesToUnicode.entrySet()) {
      unicodeToBytes.put(e.getValue().charAt(0), e.getKey());
    }

    // vocab.json
    InputStream vocabStream = form.openAssetForExtension(component, "vocab.json");
    BufferedReader vocabReader = new BufferedReader(new InputStreamReader(vocabStream, "UTF-8"));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = vocabReader.readLine()) != null) sb.append(line);
    vocabReader.close();
    JSONObject jo = new JSONObject(sb.toString());
    Iterator<String> keys = jo.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      vocab.put(k, jo.getInt(k));
    }

    // merges.txt (skip first line "#version: 0.2")
    InputStream mergesStream = form.openAssetForExtension(component, "merges.txt");
    BufferedReader mergesReader = new BufferedReader(new InputStreamReader(mergesStream, "UTF-8"));
    int rank = 0;
    boolean firstLine = true;
    while ((line = mergesReader.readLine()) != null) {
      if (firstLine) { firstLine = false; continue; }
      line = line.trim();
      int space = line.indexOf(' ');
      if (space > 0) {
        mergeRanks.put(new TokenPair(line.substring(0, space), line.substring(space + 1)), rank++);
      }
    }
    mergesReader.close();

    // build special split pattern
    StringBuilder pat = new StringBuilder("(");
    boolean first = true;
    for (String tok : specialTokens.keySet()) {
      if (!first) pat.append("|");
      pat.append(Pattern.quote(tok));
      first = false;
    }
    pat.append(")");
    specialSplitPattern = Pattern.compile(pat.toString());
  }

  public int[] encode(String text) {
    List<Integer> result = new ArrayList<>();

    // Split on special tokens, preserving delimiters
    String[] parts = specialSplitPattern.split(text, -1);
    List<String> delimiters = new ArrayList<>();
    Matcher m = specialSplitPattern.matcher(text);
    while (m.find()) delimiters.add(m.group());

    // Interleave segments and delimiters
    for (int i = 0; i < parts.length; i++) {
      String seg = parts[i];
      if (!seg.isEmpty()) {
        Integer specialId = specialTokens.get(seg);
        if (specialId != null) {
          result.add(specialId);
        } else {
          result.addAll(bpeEncode(seg));
        }
      }
      if (i < delimiters.size()) {
        result.add(specialTokens.get(delimiters.get(i)));
      }
    }

    int[] arr = new int[result.size()];
    for (int i = 0; i < arr.length; i++) arr[i] = result.get(i);
    return arr;
  }

  /** Decode a single token id back to a (possibly partial) UTF-8 string. */
  public String decodeSingle(int id) {
    String special = specialTokensReverse.get(id);
    if (special != null) return special;

    // Build reverse vocab on first use
    if (reverseVocab == null) {
      reverseVocab = new String[vocab.size()];
      for (Map.Entry<String, Integer> e : vocab.entrySet()) {
        int idx = e.getValue();
        if (idx >= 0 && idx < reverseVocab.length) {
          reverseVocab[idx] = e.getKey();
        }
      }
    }

    String token = (id >= 0 && id < reverseVocab.length) ? reverseVocab[id] : null;
    if (token == null) return "";

    // token is a sequence of unicode-mapped chars; convert back to bytes
    try {
      byte[] bytes = new byte[token.length()];
      for (int i = 0; i < token.length(); i++) {
        char c = token.charAt(i);
        Integer byteVal = unicodeToBytes.get(c);
        bytes[i] = (byte) (byteVal != null ? byteVal.intValue() : c);
      }
      return new String(bytes, "UTF-8");
    } catch (Exception e) {
      return token;
    }
  }

  private List<Integer> bpeEncode(String text) {
    List<Integer> result = new ArrayList<>();
    Matcher m = PRETOKEN_PATTERN.matcher(text);
    while (m.find()) {
      result.addAll(bpeWord(m.group()));
    }
    return result;
  }

  private List<Integer> bpeWord(String word) {
    // Convert each byte to its unicode representative char
    byte[] utf8;
    try {
      utf8 = word.getBytes("UTF-8");
    } catch (Exception e) {
      utf8 = word.getBytes();
    }
    List<String> tokens = new ArrayList<>(utf8.length);
    for (byte b : utf8) {
      tokens.add(bytesToUnicode.get(b & 0xFF));
    }

    // BPE merge loop
    while (tokens.size() > 1) {
      Set<TokenPair> pairs = adjacentPairs(tokens);
      TokenPair best = null;
      int bestRank = Integer.MAX_VALUE;
      for (TokenPair p : pairs) {
        Integer r = mergeRanks.get(p);
        if (r != null && r < bestRank) {
          bestRank = r;
          best = p;
        }
      }
      if (best == null) break;
      tokens = applyMerge(tokens, best);
    }

    List<Integer> ids = new ArrayList<>(tokens.size());
    for (String t : tokens) {
      Integer id = vocab.get(t);
      ids.add(id != null ? id : 0);
    }
    return ids;
  }

  private Set<TokenPair> adjacentPairs(List<String> tokens) {
    Set<TokenPair> pairs = new HashSet<>();
    for (int i = 0; i < tokens.size() - 1; i++) {
      pairs.add(new TokenPair(tokens.get(i), tokens.get(i + 1)));
    }
    return pairs;
  }

  private List<String> applyMerge(List<String> tokens, TokenPair pair) {
    List<String> result = new ArrayList<>();
    int i = 0;
    while (i < tokens.size()) {
      if (i < tokens.size() - 1
          && tokens.get(i).equals(pair.first)
          && tokens.get(i + 1).equals(pair.second)) {
        result.add(pair.first + pair.second);
        i += 2;
      } else {
        result.add(tokens.get(i));
        i++;
      }
    }
    return result;
  }

  static final class TokenPair {
    final String first;
    final String second;

    TokenPair(String first, String second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof TokenPair)) return false;
      TokenPair p = (TokenPair) o;
      return first.equals(p.first) && second.equals(p.second);
    }

    @Override
    public int hashCode() {
      return 31 * first.hashCode() + second.hashCode();
    }
  }
}
