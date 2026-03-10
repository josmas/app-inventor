// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2026 MIT, All rights reserved
// Released under the Apache License, Version 2.0

package edu.mit.appinventor.tinyllavademo;

import android.util.Half;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;

/**
 * Memory-mapped lookup into the float16 embedding weight file.
 *
 * The file (embed_weights.bin) contains the Qwen2 embed_tokens.weight
 * matrix serialised as a flat array of float16 values in little-endian row-major
 * order: [VOCAB_SIZE, HIDDEN] = [151936, 896].
 *
 * Requires API 26+ for android.util.Half (minSdk 28 is fine).
 * TODO: double check if we need compatibility to pre 26
 */
class EmbedLookup implements Closeable {

  static final int VOCAB_SIZE = 151936;
  static final int HIDDEN = 896;

  private final FileChannel channel;
  private final ShortBuffer buf;

  EmbedLookup(String embedWeightsPath) throws IOException {
    channel = new FileInputStream(embedWeightsPath).getChannel();
    buf = channel
        .map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer();
  }

  /**
   * Return the float32 embedding vector for tokenId as a float[] of length HIDDEN.
   */
  float[] lookup(int tokenId) {
    if (tokenId < 0 || tokenId >= VOCAB_SIZE) {
      throw new IllegalArgumentException("tokenId " + tokenId + " out of range [0, " + VOCAB_SIZE + ")");
    }
    float[] result = new float[HIDDEN];
    int offset = tokenId * HIDDEN;
    for (int i = 0; i < HIDDEN; i++) {
      result[i] = Half.toFloat(buf.get(offset + i));
    }
    return result;
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
