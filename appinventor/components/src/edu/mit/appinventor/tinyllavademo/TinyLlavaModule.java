// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2026 MIT, All rights reserved
// Released under the Apache License, Version 2.0

package edu.mit.appinventor.tinyllavademo;

import android.graphics.Bitmap;

import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.Form;

import org.pytorch.executorch.EValue;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Ties together six ExecuTorch modules (vision encoder, connector, LLM prefill,
 * LLM text-prefill, LLM decode, lm_head) plus the memory-mapped embedding lookup
 * and BPE tokenizer to run end-to-end image-grounded text generation with KV caching
 * and image KV prefix reuse.
 *
 * Large files expected at modelDirectory (default /data/local/tmp/tinyllava/):
 *   vision_encoder.pte    (~387 MB)
 *   llm_prefill.pte       (~427 MB)
 *   llm_text_prefill.pte  (~408 MB)
 *   llm_decode.pte        (~427 MB)
 *   lm_head.pte           (~131 MB)
 *   embed_weights.bin     (~260 MB)
 *
 * connector.pte (~1.8 MB) is bundled as an asset and copied to filesDir on first run.
 */
class TinyLlavaModule {

  // ---------- native library loading ------------------------------------------
  // In the companion the DexClassLoader is created with null native-library path,
  // so System.loadLibrary() cannot find bundled .so files.  We pre-load them by
  // absolute path from the companion's extension cache directory.
  private static final String EXT_PACKAGE = "edu.mit.appinventor.tinyllavademo";
  private static final String[] NATIVE_LOAD_ORDER =
      {"libc++_shared.so", "libfbjni.so", "libexecutorch.so"};

  // ---------- model dimensions ------------------------------------------------
  private static final int HIDDEN        = 896;
  private static final int VISION_HIDDEN = 1152;
  private static final int MAX_SEQ_LEN   = 300;
  private static final int N_IMAGE       = 196;
  private static final int MAX_TEXT_LEN  = MAX_SEQ_LEN - N_IMAGE;  // 104
  private static final int NUM_LAYERS    = 24;
  private static final int NUM_KV_HEADS  = 2;
  private static final int HEAD_DIM      = 64;
  private static final int KV_CACHE_SIZE = NUM_LAYERS * NUM_KV_HEADS * MAX_SEQ_LEN * HEAD_DIM;
  private static final int IMAGE_KV_SIZE = NUM_LAYERS * NUM_KV_HEADS * N_IMAGE * HEAD_DIM;

  private final Form form;
  private final Component component;

  private Module visionEncoder;
  private Module connector;
  private Module llmPrefill;
  private Module llmTextPrefill;
  private Module llmDecode;
  private Module lmHead;
  private EmbedLookup embedLookup;
  private BpeTokenizer tokenizer;

  private FloatArrayPair savedImageKV = null;
  private Bitmap lastBitmap = null;

  boolean isLoaded = false;

  TinyLlavaModule(Form form, Component component) {
    this.form = form;
    this.component = component;
  }

  void load(String modelDirectory) throws Exception {
    if (isLoaded) return;
    if (!modelDirectory.endsWith("/")) modelDirectory += "/";

    preloadNativeLibraries();
    tokenizer      = new BpeTokenizer(form, component);
    visionEncoder  = Module.load(modelDirectory + "vision_encoder.pte");
    connector      = Module.load(copyAssetToFilesDir("connector.pte").getAbsolutePath());
    llmPrefill     = Module.load(modelDirectory + "llm_prefill.pte");
    llmTextPrefill = Module.load(modelDirectory + "llm_text_prefill.pte");
    llmDecode      = Module.load(modelDirectory + "llm_decode.pte");
    lmHead         = Module.load(modelDirectory + "lm_head.pte");
    embedLookup    = new EmbedLookup(modelDirectory + "embed_weights.bin");

    isLoaded = true;
  }

  interface TokenCallback {
    void onToken(String token);
  }

  void generate(Bitmap bitmap, String question, int maxNewTokens, TokenCallback onToken)
      throws Exception {
    if (!isLoaded) throw new IllegalStateException("Call load() before generate()");

    long t0 = System.nanoTime();

    int[] tokenIds = tokenizer.encode(buildPrompt(question));
    int N = tokenIds.length;
    if (N > MAX_TEXT_LEN) {
      throw new IllegalArgumentException(
          "Text too long: " + N + " tokens (max " + MAX_TEXT_LEN + "). Shorten your question.");
    }

    float[] textEmbeds = new float[N * HIDDEN];
    for (int i = 0; i < tokenIds.length; i++) {
      float[] vec = embedLookup.lookup(tokenIds[i]);
      System.arraycopy(vec, 0, textEmbeds, i * HIDDEN, HIDDEN);
    }

    boolean sameImage = (bitmap == lastBitmap && savedImageKV != null);

    float[] kCacheArr;
    float[] vCacheArr;
    int firstId;
    int cachePos;

    if (!sameImage) {
      float[] pixels = ImagePreprocessor.preprocess(bitmap);
      Tensor pixelTensor = Tensor.fromBlob(pixels, new long[]{1, 3, 384, 384});

      float[] imgFeatArray = visionEncoder.forward(EValue.from(pixelTensor))[0]
          .toTensor().getDataAsFloatArray();
      int actualImgSeqLen = imgFeatArray.length / VISION_HIDDEN;

      Tensor imgFeatTensor = Tensor.fromBlob(
          imgFeatArray, new long[]{1, actualImgSeqLen, VISION_HIDDEN});
      float[] imgEmbeds = connector.forward(EValue.from(imgFeatTensor))[0]
          .toTensor().getDataAsFloatArray();

      int validLen = actualImgSeqLen + N;
      if (validLen > MAX_SEQ_LEN) {
        throw new IllegalArgumentException(
            "Sequence too long: " + validLen + " tokens (max " + MAX_SEQ_LEN + ").");
      }

      float[] combined = new float[MAX_SEQ_LEN * HIDDEN];
      System.arraycopy(imgEmbeds, 0, combined, 0, imgEmbeds.length);
      System.arraycopy(textEmbeds, 0, combined, actualImgSeqLen * HIDDEN, textEmbeds.length);

      long[] prefillMask = new long[MAX_SEQ_LEN];
      long[] prefillPos  = new long[MAX_SEQ_LEN];
      for (int i = 0; i < MAX_SEQ_LEN; i++) {
        prefillMask[i] = (i < validLen) ? 1L : 0L;
        prefillPos[i]  = i;
      }

      Tensor embedsTensor = Tensor.fromBlob(combined,    new long[]{1, MAX_SEQ_LEN, HIDDEN});
      Tensor attnTensor   = Tensor.fromBlob(prefillMask, new long[]{1, MAX_SEQ_LEN});
      Tensor posTensor    = Tensor.fromBlob(prefillPos,  new long[]{1, MAX_SEQ_LEN});

      EValue[] prefillOut = llmPrefill.forward(
          EValue.from(embedsTensor), EValue.from(attnTensor), EValue.from(posTensor));

      float[] hiddenStates = prefillOut[0].toTensor().getDataAsFloatArray();
      kCacheArr = prefillOut[1].toTensor().getDataAsFloatArray();
      vCacheArr = prefillOut[2].toTensor().getDataAsFloatArray();

      saveImageKVCache(kCacheArr, vCacheArr);
      lastBitmap = bitmap;

      float[] lastHidden = new float[HIDDEN];
      System.arraycopy(hiddenStates, (validLen - 1) * HIDDEN, lastHidden, 0, HIDDEN);
      float[] logits = lmHead.forward(
          EValue.from(Tensor.fromBlob(lastHidden, new long[]{1, HIDDEN})))[0]
          .toTensor().getDataAsFloatArray();
      firstId  = argmax(logits);
      cachePos = validLen;

    } else {
      // Same-image fast path
      kCacheArr = savedImageKV.k.clone();
      vCacheArr = savedImageKV.v.clone();
      // Restore from compact image KV into full cache
      float[] k = new float[KV_CACHE_SIZE];
      float[] v = new float[KV_CACHE_SIZE];
      for (int l = 0; l < NUM_LAYERS; l++) {
        for (int h = 0; h < NUM_KV_HEADS; h++) {
          int srcBase = (l * NUM_KV_HEADS + h) * N_IMAGE * HEAD_DIM;
          int dstBase = ((l * NUM_KV_HEADS + h) * MAX_SEQ_LEN) * HEAD_DIM;
          System.arraycopy(savedImageKV.k, srcBase, k, dstBase, N_IMAGE * HEAD_DIM);
          System.arraycopy(savedImageKV.v, srcBase, v, dstBase, N_IMAGE * HEAD_DIM);
        }
      }
      kCacheArr = k;
      vCacheArr = v;

      float[] textEmbedsPadded = new float[MAX_TEXT_LEN * HIDDEN];
      System.arraycopy(textEmbeds, 0, textEmbedsPadded, 0, textEmbeds.length);

      long[] textMask = new long[MAX_SEQ_LEN + MAX_TEXT_LEN];
      for (int i = 0; i < textMask.length; i++) {
        if      (i < N_IMAGE)               textMask[i] = 1L;
        else if (i < MAX_SEQ_LEN)           textMask[i] = 0L;
        else if (i < MAX_SEQ_LEN + N)       textMask[i] = 1L;
        else                                textMask[i] = 0L;
      }

      long[] posIds = new long[MAX_TEXT_LEN];
      for (int i = 0; i < MAX_TEXT_LEN; i++) posIds[i] = N_IMAGE + i;

      Tensor textEmbedsTensor = Tensor.fromBlob(textEmbedsPadded, new long[]{1, MAX_TEXT_LEN, HIDDEN});
      Tensor kCacheTensor     = Tensor.fromBlob(kCacheArr,        new long[]{NUM_LAYERS, 1, NUM_KV_HEADS, MAX_SEQ_LEN, HEAD_DIM});
      Tensor vCacheTensor     = Tensor.fromBlob(vCacheArr,        new long[]{NUM_LAYERS, 1, NUM_KV_HEADS, MAX_SEQ_LEN, HEAD_DIM});
      Tensor maskTensor       = Tensor.fromBlob(textMask,         new long[]{1, MAX_SEQ_LEN + MAX_TEXT_LEN});
      Tensor posIdsTensor     = Tensor.fromBlob(posIds,           new long[]{1, MAX_TEXT_LEN});

      EValue[] textPrefillOut = llmTextPrefill.forward(
          EValue.from(textEmbedsTensor),
          EValue.from(kCacheTensor),
          EValue.from(vCacheTensor),
          EValue.from(maskTensor),
          EValue.from(posIdsTensor));

      float[] hidden = textPrefillOut[0].toTensor().getDataAsFloatArray();
      float[] newK   = textPrefillOut[1].toTensor().getDataAsFloatArray();
      float[] newV   = textPrefillOut[2].toTensor().getDataAsFloatArray();

      writeTextKVToCache(newK, newV, kCacheArr, vCacheArr);

      float[] lastHidden = new float[HIDDEN];
      System.arraycopy(hidden, (N - 1) * HIDDEN, lastHidden, 0, HIDDEN);
      float[] logits = lmHead.forward(
          EValue.from(Tensor.fromBlob(lastHidden, new long[]{1, HIDDEN})))[0]
          .toTensor().getDataAsFloatArray();
      firstId  = argmax(logits);
      cachePos = N_IMAGE + N;
    }

    // Emit first token
    int generatedCount = 0;
    if (firstId != tokenizer.eosTokenId) {
      onToken.onToken(tokenizer.decodeSingle(firstId));
      generatedCount++;
    }

    // KV-cached decode loop
    int prevId = firstId;
    long[] decodeMask = new long[MAX_SEQ_LEN + 1];

    while (generatedCount < maxNewTokens && cachePos < MAX_SEQ_LEN) {
      for (int i = 0; i <= MAX_SEQ_LEN; i++) {
        if      (i < cachePos)   decodeMask[i] = 1L;
        else if (i < MAX_SEQ_LEN) decodeMask[i] = 0L;
        else                      decodeMask[i] = 1L;
      }

      float[] nextEmbed   = embedLookup.lookup(prevId);
      Tensor embedTensor  = Tensor.fromBlob(nextEmbed, new long[]{1, 1, HIDDEN});
      Tensor kCacheTensor = Tensor.fromBlob(kCacheArr, new long[]{NUM_LAYERS, 1, NUM_KV_HEADS, MAX_SEQ_LEN, HEAD_DIM});
      Tensor vCacheTensor = Tensor.fromBlob(vCacheArr, new long[]{NUM_LAYERS, 1, NUM_KV_HEADS, MAX_SEQ_LEN, HEAD_DIM});
      Tensor maskTensor   = Tensor.fromBlob(decodeMask, new long[]{1, MAX_SEQ_LEN + 1});
      Tensor posIdTensor  = Tensor.fromBlob(new long[]{cachePos}, new long[]{1, 1});

      EValue[] decodeOut = llmDecode.forward(
          EValue.from(embedTensor),
          EValue.from(kCacheTensor),
          EValue.from(vCacheTensor),
          EValue.from(maskTensor),
          EValue.from(posIdTensor));

      float[] newHidden = decodeOut[0].toTensor().getDataAsFloatArray();
      float[] newK      = decodeOut[1].toTensor().getDataAsFloatArray();
      float[] newV      = decodeOut[2].toTensor().getDataAsFloatArray();
      float[] logits = lmHead.forward(
          EValue.from(Tensor.fromBlob(newHidden, new long[]{1, HIDDEN})))[0]
          .toTensor().getDataAsFloatArray();

      for (int layer = 0; layer < NUM_LAYERS; layer++) {
        for (int h = 0; h < NUM_KV_HEADS; h++) {
          int srcOff = (layer * NUM_KV_HEADS + h) * HEAD_DIM;
          int dstOff = ((layer * NUM_KV_HEADS + h) * MAX_SEQ_LEN + cachePos) * HEAD_DIM;
          System.arraycopy(newK, srcOff, kCacheArr, dstOff, HEAD_DIM);
          System.arraycopy(newV, srcOff, vCacheArr, dstOff, HEAD_DIM);
        }
      }

      int nextId = argmax(logits);
      if (nextId == tokenizer.eosTokenId) break;

      onToken.onToken(tokenizer.decodeSingle(nextId));
      prevId = nextId;
      cachePos++;
      generatedCount++;
    }
  }

  private void saveImageKVCache(float[] kArr, float[] vArr) {
    float[] savedK = new float[IMAGE_KV_SIZE];
    float[] savedV = new float[IMAGE_KV_SIZE];
    for (int l = 0; l < NUM_LAYERS; l++) {
      for (int h = 0; h < NUM_KV_HEADS; h++) {
        int srcBase = ((l * NUM_KV_HEADS + h) * MAX_SEQ_LEN) * HEAD_DIM;
        int dstBase = (l * NUM_KV_HEADS + h) * N_IMAGE * HEAD_DIM;
        System.arraycopy(kArr, srcBase, savedK, dstBase, N_IMAGE * HEAD_DIM);
        System.arraycopy(vArr, srcBase, savedV, dstBase, N_IMAGE * HEAD_DIM);
      }
    }
    savedImageKV = new FloatArrayPair(savedK, savedV);
  }

  private void writeTextKVToCache(float[] newK, float[] newV, float[] kArr, float[] vArr) {
    for (int l = 0; l < NUM_LAYERS; l++) {
      for (int h = 0; h < NUM_KV_HEADS; h++) {
        int srcBase = (l * NUM_KV_HEADS + h) * MAX_TEXT_LEN * HEAD_DIM;
        int dstBase = ((l * NUM_KV_HEADS + h) * MAX_SEQ_LEN + N_IMAGE) * HEAD_DIM;
        System.arraycopy(newK, srcBase, kArr, dstBase, MAX_TEXT_LEN * HEAD_DIM);
        System.arraycopy(newV, srcBase, vArr, dstBase, MAX_TEXT_LEN * HEAD_DIM);
      }
    }
  }

  private String buildPrompt(String question) {
    return "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n"
        + "<|im_start|>user\n" + question + "<|im_end|>\n"
        + "<|im_start|>assistant\n";
  }

  private int argmax(float[] logits) {
    int bestIdx = 0;
    float bestVal = logits[0];
    for (int i = 1; i < logits.length; i++) {
      if (logits[i] > bestVal) { bestVal = logits[i]; bestIdx = i; }
    }
    return bestIdx;
  }

  private void preloadNativeLibraries() {
    // Try the companion cache directory (Android 14+: getCacheDir()/external_comps/<pkg>/files/arm64-v8a/)
    // For built APKs the path won't exist and Android loads .so files normally via System.loadLibrary.
    String nativeDir = form.getCacheDir().getAbsolutePath()
        + "/external_comps/" + EXT_PACKAGE + "/files/arm64-v8a/";
    for (String lib : NATIVE_LOAD_ORDER) {
      File f = new File(nativeDir, lib);
      if (f.exists()) {
        try {
          System.load(f.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
          // Already loaded by a previous call, or wrong ABI — safe to ignore.
        }
      }
    }
  }

  private File copyAssetToFilesDir(String assetName) throws Exception {
    File dest = new File(form.getFilesDir(), assetName);
    if (!dest.exists()) {
      InputStream in = form.openAssetForExtension(component, assetName);
      OutputStream out = new FileOutputStream(dest);
      byte[] buf = new byte[32768];
      int len;
      while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
      in.close();
      out.close();
    }
    return dest;
  }

  // ---------- simple pair for KV cache storage --------------------------------
  private static final class FloatArrayPair {
    final float[] k;
    final float[] v;
    FloatArrayPair(float[] k, float[] v) { this.k = k; this.v = v; }
  }
}
