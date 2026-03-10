// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2026 MIT, All rights reserved
// Released under the Apache License, Version 2.0

package edu.mit.appinventor.tinyllavademo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesAssets;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesNativeLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.util.AsynchUtil;

/**
 * TinyLlavaDemo is a non-visible extension component that exposes on-device
 * vision-language inference using TinyLLaVA / ExecuTorch to App Inventor apps.
 *
 * For now this is an skeleton with stubbed inference — fires events but does no ML.
 */
@DesignerComponent(
    version = 1,
    category = ComponentCategory.EXTENSION,
    description = "A non-visible component for on-device vision-language inference " +
        "using TinyLLaVA and ExecuTorch. Requires model files on-device at ModelDirectory.",
    nonVisible = true,
    iconName = "images/extension.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "executorch-android.jar, fbjni.jar, nativeloader.jar")
@UsesNativeLibraries(v8aLibraries = "libexecutorch.so, libfbjni.so, libc++_shared.so")
@UsesAssets(fileNames = "vocab.json, merges.txt, connector.pte")
@UsesPermissions(permissionNames =
    "android.permission.READ_EXTERNAL_STORAGE," +
    "android.permission.READ_MEDIA_IMAGES")
public class TinyLlavaDemo extends AndroidNonvisibleComponent {

  private static final String LOG_TAG = "TinyLlavaDemo";
  private static final String DEFAULT_MODEL_DIR = "/data/local/tmp/tinyllava/";
  private static final int DEFAULT_MAX_TOKENS = 100;

  private String modelDirectory = DEFAULT_MODEL_DIR;
  private int maxTokens = DEFAULT_MAX_TOKENS;
  private boolean isReady = false;
  private TinyLlavaModule module = null;

  public TinyLlavaDemo(ComponentContainer container) {
    super(container.$form());
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
      defaultValue = DEFAULT_MODEL_DIR)
  @SimpleProperty(description = "Directory on the device where model .pte files are stored.",
      category = PropertyCategory.BEHAVIOR)
  public void ModelDirectory(String path) {
    modelDirectory = path;
  }

  @SimpleProperty(description = "Directory on the device where model .pte files are stored.",
      category = PropertyCategory.BEHAVIOR)
  public String ModelDirectory() {
    return modelDirectory;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER,
      defaultValue = "" + DEFAULT_MAX_TOKENS)
  @SimpleProperty(description = "Maximum number of tokens to generate.",
      category = PropertyCategory.BEHAVIOR)
  public void MaxTokens(int tokens) {
    maxTokens = tokens;
  }

  @SimpleProperty(description = "Maximum number of tokens to generate.",
      category = PropertyCategory.BEHAVIOR)
  public int MaxTokens() {
    return maxTokens;
  }

  @SimpleProperty(description = "True when the model has been loaded and is ready to use.",
      category = PropertyCategory.BEHAVIOR)
  public boolean IsReady() {
    return isReady;
  }

  @SimpleFunction(description = "Load the TinyLLaVA model from ModelDirectory. " +
      "Fires ModelLoaded when complete, or GotError on failure.")
  public void LoadModel() {
    isReady = false;
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run() {
        try {
          module = new TinyLlavaModule(form, TinyLlavaDemo.this);
          module.load(modelDirectory);
          isReady = true;
          form.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              ModelLoaded();
            }
          });
        } catch (Exception e) {
          Log.e(LOG_TAG, "LoadModel error", e);
          final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
          form.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              GotError(msg);
            }
          });
        }
      }
    });
  }

  @SimpleFunction(description = "Analyze an image with the given prompt using default MaxTokens. " +
      "Fires GotToken for each output token and GotResponse with the full response.")
  public void Analyze(final String imagePath, final String prompt) {
    Analyze(imagePath, prompt, maxTokens);
  }

  @SimpleFunction(description = "Analyze an image with the given prompt and token limit. " +
      "Fires GotToken for each output token and GotResponse with the full response.")
  public void Analyze(final String imagePath, final String prompt, final int maxTokens) {
    if (!isReady) {
      GotError("Model is not loaded. Call LoadModel first.");
      return;
    }
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run() {
        try {
          Bitmap bmp = BitmapFactory.decodeFile(imagePath);
          if (bmp == null) {
            throw new Exception("Cannot decode image: " + imagePath);
          }
          final StringBuilder full = new StringBuilder();
          module.generate(bmp, prompt, maxTokens, new TinyLlavaModule.TokenCallback() {
            @Override
            public void onToken(final String token) {
              full.append(token);
              form.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  GotToken(token);
                }
              });
            }
          });
          final String result = full.toString();
          form.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              GotResponse(result);
            }
          });
        } catch (Exception e) {
          Log.e(LOG_TAG, "Analyze error", e);
          final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
          form.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              GotError(msg);
            }
          });
        }
      }
    });
  }

  @SimpleEvent(description = "Fired when the model has been loaded successfully.")
  public void ModelLoaded() {
    EventDispatcher.dispatchEvent(this, "ModelLoaded");
  }

  @SimpleEvent(description = "Fired for each token generated during inference.")
  public void GotToken(String text) {
    EventDispatcher.dispatchEvent(this, "GotToken", text);
  }

  @SimpleEvent(description = "Fired when inference is complete with the full response text.")
  public void GotResponse(String fullText) {
    EventDispatcher.dispatchEvent(this, "GotResponse", fullText);
  }

  @SimpleEvent(description = "Fired when an error occurs during model loading or inference.")
  public void GotError(String message) {
    EventDispatcher.dispatchEvent(this, "GotError", message);
  }
}
