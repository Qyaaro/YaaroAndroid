package com.yaaro.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class YaaroFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "YaaroFCM";
    public static final String CHANNEL_ID = "yaaro_calls_v3";

    public static final String ACTION_ACCEPT = "com.yaaro.app.ACTION_ACCEPT_CALL";
    public static final String ACTION_DECLINE = "com.yaaro.app.ACTION_DECLINE_CALL";
    public static final String EXTRA_CALL_ID = "callId";
    public static final String EXTRA_FCM_ACTION = "fcm_action";
    public static final String EXTRA_FROM_NAME = "fromName";

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "FCM token: " + token);
        getSharedPreferences("yaaro", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String title = "Yaaro";
        String body = "Incoming call";
        String type = "call";
        String callId = "";
        String fromName = "";

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null) {
                body = message.getNotification().getBody();
            }
        }

        Map<String, String> data = message.getData();
        if (data != null) {
            if (data.containsKey("title")) title = data.get("title");
            if (data.containsKey("body")) body = data.get("body");
            if (data.containsKey("type")) type = data.get("type");
            if (data.containsKey("callId")) callId = data.get("callId");
            if (data.containsKey("fromName")) fromName = data.get("fromName");
        }

        if ("call_cancel".equals(type)) {
            YaaroRingtonePlayer.stop();
            cancelCallNotification(this, callId);
            return;
        }

        if ("call".equals(type)) {
            YaaroRingtonePlayer.start(this);
            showIncomingCallNotification(title, body, callId, fromName);
        }
    }

    private void showIncomingCallNotification(
            String title, String body, String callId, String fromName
    ) {
        createChannel();

        int notifyId = notifyIdFor(callId);

        // Notification body tap opens app; action buttons use separate intents
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction("com.yaaro.app.ACTION_VIEW_CALL");
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openIntent.putExtra(EXTRA_FCM_ACTION, "view");
        openIntent.putExtra(EXTRA_CALL_ID, callId);
        openIntent.putExtra(EXTRA_FROM_NAME, fromName);

        PendingIntent contentPending = PendingIntent.getActivity(
                this,
                notifyId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent acceptIntent = new Intent(this, MainActivity.class);
        acceptIntent.setAction(ACTION_ACCEPT);
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        acceptIntent.putExtra(EXTRA_FCM_ACTION, "accept");
        acceptIntent.putExtra(EXTRA_CALL_ID, callId);
        acceptIntent.putExtra(EXTRA_FROM_NAME, fromName);

        PendingIntent acceptPending = PendingIntent.getActivity(
                this,
                notifyId + 1,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Decline → Receiver (ring stop + open app with decline)
        Intent declineIntent = new Intent(this, CallActionReceiver.class);
        declineIntent.setAction(ACTION_DECLINE);
        declineIntent.putExtra(EXTRA_CALL_ID, callId);
        declineIntent.putExtra(EXTRA_FCM_ACTION, "decline");

        PendingIntent declinePending = PendingIntent.getBroadcast(
                this,
                notifyId + 2,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle(title != null ? title : "Yaaro")
                .setContentText(body != null ? body : "Incoming call")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPending)
                .addAction(0, "Decline", declinePending)
                .addAction(0, "Accept", acceptPending);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(notifyId, builder.build());
        }
    }

    public static int notifyIdFor(String callId) {
        if (callId == null || callId.isEmpty()) return 44001;
        return 44000 + Math.abs(callId.hashCode() % 100000);
    }

    public static void cancelCallNotification(Context context, String callId) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(notifyIdFor(callId));
            if (callId == null || callId.isEmpty()) {
                nm.cancel(44001);
            }
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Yaaro Calls",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Incoming voice and video calls");
            channel.enableVibration(true);
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
