package com.google.appinventor.components.runtime;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import android.util.Log;
import org.jboss.aerogear.android.unifiedpush.MessageHandler;
import org.jboss.aerogear.android.unifiedpush.gcm.UnifiedPushMessage;

public class AerogearPushNotifyingHandler implements MessageHandler {

  private static final String TAG = "AerogearPush";
  public static final int NOTIFICATION_ID = 8648; // Following from SmsBroadcastReceiver.java
  private Context context;

  public static final AerogearPushNotifyingHandler instance = new AerogearPushNotifyingHandler();

  public AerogearPushNotifyingHandler() {
  }

  @Override
  public void onMessage(Context context, Bundle bundle) {
    this.context = context;

    String message = bundle.getString(UnifiedPushMessage.ALERT_KEY);
    notify(message);
  }

  private void notify(String message) {

    Class<?> forName;
    try {
      forName = Class.forName(context.getPackageName() + ".Screen1");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      // Not much we can do here? We could Toast but the Activity might not be up. Log and exit.
      Log.e(TAG, "Could not find the class " + context.getPackageName() + ".Screen1: Exiting Notification.");
      return;
    }
    Intent intent = new Intent(context, forName)
        .addFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        .putExtra(UnifiedPushMessage.ALERT_KEY, message);

    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    Notification note = new Notification(R.drawable.stat_notify_voicemail, message, System.currentTimeMillis());
    note.flags |= Notification.FLAG_AUTO_CANCEL;
    note.defaults |= Notification.DEFAULT_SOUND;

    PendingIntent pendingActivity = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    note.setLatestEventInfo(context, "Notification from AeroGear", message, pendingActivity);
    nm.notify(NOTIFICATION_ID, note);

    // Send the message to the component for caching.
    AerogearPush.cacheReceivedMessage(message);
  }

  @Override
  public void onDeleteMessage(Context context, Bundle bundle) {
    // handle GoogleCloudMessaging.MESSAGE_TYPE_DELETED
  }

  @Override
  public void onError() {
    // handle GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR
  }

}