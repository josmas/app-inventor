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
  public static final int NOTIFICATION_ID = 8648; // Following from SmsBroadcastReceiver
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
    NotificationManager mNotificationManager = (NotificationManager)
        context.getSystemService(Context.NOTIFICATION_SERVICE);

    String classname = context.getPackageName() + ".Screen1";
    Intent intent = null;
    try {
      intent = new Intent(context, Class.forName(classname))
          .addFlags(PendingIntent.FLAG_UPDATE_CURRENT)
          .putExtra(UnifiedPushMessage.ALERT_KEY, message);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      //TODO (jos) handle the exception properly
    }

    intent.setAction(Intent.ACTION_MAIN);
    intent.addCategory(Intent.CATEGORY_LAUNCHER);

    // These flags seem to work, but maybe there's a way to improve on this?
    // NEW_TASK: the activity will become a new task on activity stack
    // SINGLE_TOP: activity won't be launched if already on top of stack
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    // Create the Notification
    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    Notification note = new Notification(R.drawable.sym_call_incoming, " : the message goes here somehow ", System.currentTimeMillis());
    note.flags |= Notification.FLAG_AUTO_CANCEL;
    note.defaults |= Notification.DEFAULT_SOUND;

    PendingIntent activity = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    note.setLatestEventInfo(context, "Sms from ????", "the message here", activity);
    note.number = Texting.getCachedMsgCount();
    nm.notify(null, NOTIFICATION_ID, note);
    Log.i(TAG, "Notification sent, classname: " + classname);
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