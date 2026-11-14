package com.yaaro.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Reserved lifecycle owner for a future native WebRTC call engine.
 *
 * Do not start this service from a notification action yet: it does not
 * establish a peer connection, capture microphone audio, or accept calls.
 * Background microphone capture requires a correctly configured foreground
 * service and an Android-version-aware permission/start flow.
 */
public final class NativeCallService extends Service {
    private static final String TAG = "YaaroNativeCall";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.w(TAG, "Native audio engine not installed; refusing background call start");
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
