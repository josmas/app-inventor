// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.components.runtime;

import android.util.Log;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;


import android.view.View;

@DesignerComponent(version = YaVersion.MQTT_COMPONENT_VERSION,
    description = "A new component ",
    category = ComponentCategory.EXPERIMENTAL,
    nonVisible = true,
    iconName = "images/mqtt.png")
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.INTERNET, android.permission.WAKE_LOCK," +
    "android.permission.ACCESS_NETWORK_STATE")
@UsesLibraries(libraries = "paho-service.jar, paho-client.jar, android-support-v4.jar, android-support-v7-appcompat.jar")
public final class MQTT extends AndroidNonvisibleComponent {

  public static final String TAG = "MQTT";

  /**
   * Creates a new component.
   *
   * @param container  container, component will be placed in
   */
  public MQTT(ComponentContainer container) {
    super(container.$form());
    Log.d(TAG, "MQTT: call on component constructor");
    MQTTConnection conn = new MQTTConnection(container.$context());
    Log.d(TAG, "MQTT: after creating the connection");
    conn.connectMqtt();
    Log.d(TAG, "MQTT: after having called connectMqtt()");
  }

}
