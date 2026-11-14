package com.yaaro.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Microphone foreground-service lifecycle for an already authorized native call.
 * Only a visible, eligible Activity may request ACTION_START after verifying
 * RECORD_AUDIO and call authorization. Notification actions must NOT start it.
 * Signaling/WebRTC ownership will be attached after native auth is implemented.
 */
public final class NativeCallService extends Service {
    public static final String ACTION_START = "com.yaaro.app.NATIVE_CALL_START";
    public static final String ACTION_STOP = "com.yaaro.app.NATIVE_CALL_STOP";
    public static final String EXTRA_CALL_ID = "native_call_id";
    private static final String CHANNEL_ID = "yaaro_native_active_call";
    private static final int NOTIFICATION_ID = 2109;
    private static final String TAG = "YaaroNativeCall";
    private String activeCallId;
    private static volatile String authorizedCallId;

    /** In-process authorization only; never accept a call ID from an untrusted Intent alone. */
    public static synchronized void authorizeFromVisibleCall(String callId) {
        if (callId == null || callId.isEmpty()) {
            throw new IllegalArgumentException("Call ID required");
        }
        authorizedCallId = callId;
    }

    public static synchronized void revokeAuthorization(String callId) {
        if (callId != null && callId.equals(authorizedCallId)) authorizedCallId = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            String requestedId = intent.getStringExtra(EXTRA_CALL_ID);
            if (activeCallId != null && activeCallId.equals(requestedId)) {
                stopForeground(true);
                activeCallId = null;
                revokeAuthorization(requestedId);
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String callId = intent.getStringExtra(EXTRA_CALL_ID);
        if (callId == null || callId.isEmpty() ||
                !callId.equals(authorizedCallId) ||
                (activeCallId != null && !activeCallId.equals(callId)) ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Rejecting unverified microphone service start");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, "Active voice call", NotificationManager.IMPORTANCE_LOW));
            }
            Intent open = new Intent(this, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("QYAARO voice call")
                    .setContentText("Microphone service active")
                    .setContentIntent(pending)
                    .setOngoing(true)
                    .build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            activeCallId = callId;
        } catch (RuntimeException error) {
            Log.e(TAG, "Microphone foreground service start denied", error);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        revokeAuthorization(activeCallId);
        activeCallId = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
