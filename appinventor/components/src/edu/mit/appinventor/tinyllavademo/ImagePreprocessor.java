// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2026 MIT, All rights reserved
// Released under the Apache License, Version 2.0

package edu.mit.appinventor.tinyllavademo;

import android.graphics.Bitmap;

/**
 * Preprocesses a Bitmap into a float32 NCHW tensor matching the SigLIP
 * so400m-patch14-384 preprocessor_config.json:
 *
 *   image_mean = [0.5, 0.5, 0.5]
 *   image_std  = [0.5, 0.5, 0.5]
 *   → normalize(x) = (x/255 - 0.5) / 0.5  =  x/127.5 - 1.0
 *
 * Output shape: [1, 3, 384, 384]  (batch=1, C, H, W)
 */
final class ImagePreprocessor {

  private static final int TARGET_SIZE = 384;
  private static final int CHANNELS = 3;
  private static final int PIXELS = TARGET_SIZE * TARGET_SIZE;  // 147456

  private ImagePreprocessor() {}

  /**
   * Returns a float[] of length 1 × 3 × 384 × 384 = 442368 in NCHW order:
   * red plane, then green plane, then blue plane.
   */
  static float[] preprocess(Bitmap bitmap) {
    Bitmap scaled;
    if (bitmap.getWidth() == TARGET_SIZE && bitmap.getHeight() == TARGET_SIZE) {
      scaled = bitmap;
    } else {
      scaled = Bitmap.createScaledBitmap(bitmap, TARGET_SIZE, TARGET_SIZE, true);
    }

    float[] result = new float[CHANNELS * PIXELS];
    int rOffset = 0;
    int gOffset = PIXELS;
    int bOffset = PIXELS * 2;

    for (int y = 0; y < TARGET_SIZE; y++) {
      for (int x = 0; x < TARGET_SIZE; x++) {
        int pixel = scaled.getPixel(x, y);
        int idx = y * TARGET_SIZE + x;
        result[rOffset + idx] = ((pixel >> 16 & 0xFF) / 127.5f) - 1.0f;
        result[gOffset + idx] = ((pixel >>  8 & 0xFF) / 127.5f) - 1.0f;
        result[bOffset + idx] = ((pixel       & 0xFF) / 127.5f) - 1.0f;
      }
    }

    if (scaled != bitmap) scaled.recycle();

    return result;
  }
}
