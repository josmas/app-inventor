// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;


import android.view.View;

@DesignerComponent(version = YaVersion.AEROGEARPUSH_COMPONENT_VERSION,
    description = "A new component ",
    category = ComponentCategory.EXPERIMENTAL,
    nonVisible = true,
    iconName = "images/aerogearPush.png")
@SimpleObject
public final class AerogearPush extends AndroidNonvisibleComponent {

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
