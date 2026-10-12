package com.yaaro.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class YaaroFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "YaaroFCM";
    private static final String CHANNEL_ID = "yaaro_calls";

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "FCM token: " + token);
        // Token web/login se DB mein save hoga (agle step)
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

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null) {
                body = message.getNotification().getBody();
            }
        }
        if (message.getData() != null) {
            if (message.getData().containsKey("title")) {
                title = message.getData().get("title");
            }
            if (message.getData().containsKey("body")) {
                body = message.getData().get("body");
            }
            if (message.getData().containsKey("type")) {
                type = message.getData().get("type");
            }
        }

        showNotification(title, body, type);
    }

    private void showNotification(String title, String body, String type) {
        createChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("fcm_type", type);

        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(pending);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) System.currentTimeMillis(), builder.build());
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
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
