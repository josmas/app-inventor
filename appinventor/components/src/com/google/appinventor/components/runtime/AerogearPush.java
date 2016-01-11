// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.components.runtime;

import android.util.Log;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;

import org.jboss.aerogear.android.unifiedpush.RegistrarManager;
import org.jboss.aerogear.android.unifiedpush.gcm.AeroGearGCMPushJsonConfiguration;
import org.jboss.aerogear.android.core.Callback;

@DesignerComponent(version = YaVersion.AEROGEARPUSH_COMPONENT_VERSION,
    description = "A new component ",
    category = ComponentCategory.EXPERIMENTAL,
    nonVisible = true,
    iconName = "images/aerogearPush.png")
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.INTERNET, android.permission.GET_ACCOUNTS," +
    "android.permission.WAKE_LOCK, com.google.android.c2dm.permission.RECEIVE")
@UsesLibraries(libraries = "aerogear-android-push.jar, aerogear-android-core.jar, stripped-play-services.jar")
public final class AerogearPush extends AndroidNonvisibleComponent {

  private static final String PUSH_REGISTRAR_NAME = "myPushRegistrar";
  private static final String TAG = "AerogearPush";

  //TODO (jos) properties copied from Twitter; change names and docs accordingly.
  private String consumerKey = "";
  private String consumerSecret = "";

  /**
   * Creates a new component.
   *
   * @param container  container, component will be placed in
   */
  public AerogearPush(ComponentContainer container) {
    super(container.$form());

    //TODO (jos) This component only works with API 16+ : make the appropriate checks.
    RegistrarManager.config(PUSH_REGISTRAR_NAME, AeroGearGCMPushJsonConfiguration.class)
        .loadConfigJson(container.$context()) //TODO (jos) right now it is not finding the json file in assets
        .asRegistrar();

    RegistrarManager.getRegistrar(PUSH_REGISTRAR_NAME).register(container.$context().getApplicationContext(),
        new Callback<Void>() {
      @Override
      public void onSuccess(Void data) {
        Log.d(TAG, "Registration Succeeded");
        Log.d(TAG, "Registration Succeeded");
        Log.d(TAG, "Registration Succeeded");
//        Toast.makeText(getApplicationContext(), "Yay, Device registered", Toast.LENGTH_LONG).show();
      }

      @Override
      public void onFailure(Exception e) {
        Log.e(TAG, e.getMessage(), e);
        Log.e(TAG, e.getMessage(), e);
        Log.e(TAG, e.getMessage(), e);
//        Toast.makeText(getApplicationContext(), "Ops, something is wrong :(", Toast.LENGTH_LONG).show();
      }
    });
  }

  /**
   * ConsumerKey property getter method.
   */
  @SimpleProperty(category = PropertyCategory.BEHAVIOR)
  public String ConsumerKey() {
    return consumerKey;
  }

  /**
   * ConsumerKey property setter method: sets the consumer key to be used when
   * authorizing with Twitter via OAuth.
   *
   * @param consumerKey
   *          the key for use in Twitter OAuth
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
  @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "The the consumer key to be used when authorizing with Twitter via OAuth.")
  public void ConsumerKey(String consumerKey) {
    this.consumerKey = consumerKey;
  }

  /**
   * ConsumerSecret property getter method.
   */
  @SimpleProperty(category = PropertyCategory.BEHAVIOR)
  public String ConsumerSecret() {
    return consumerSecret;
  }

  /**
   * ConsumerSecret property setter method: sets the consumer secret to be used
   * when authorizing with Twitter via OAuth.
   *
   * @param consumerSecret
   *          the secret for use in Twitter OAuth
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
  @SimpleProperty(description="The consumer secret to be used when authorizing with Twitter via OAuth")
  public void ConsumerSecret(String consumerSecret) {
    this.consumerSecret = consumerSecret;
  }


}
