package com.yaaro.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Permission and audio routing helpers for an explicitly user-authorized
 * native voice session. This does not start microphone capture or bypass
 * Android background foreground-service restrictions.
 */
public final class NativeVoiceAudioPolicy {
    private NativeVoiceAudioPolicy() { }

    public static boolean hasMicrophonePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requireMicrophonePermission(Context context) {
        if (!hasMicrophonePermission(context)) {
            throw new SecurityException("RECORD_AUDIO permission required");
        }
    }

    public static void enterCallAudioMode(Context context) {
        requireMicrophonePermission(context);
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) throw new IllegalStateException("Audio manager unavailable");
        manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    public static void restoreAudioMode(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager != null) manager.setMode(AudioManager.MODE_NORMAL);
    }

    /** On Android 12+, select the built-in speaker using the communication API. */
    public static boolean setSpeakerEnabled(Context context, boolean enabled) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (android.media.AudioDeviceInfo device : manager.getAvailableCommunicationDevices()) {
                if (device.getType() == (enabled
                        ? android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        : android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)) {
                    return manager.setCommunicationDevice(device);
                }
            }
            return false;
        }
        manager.setSpeakerphoneOn(enabled);
        return true;
    }

    public static void clearCommunicationDevice(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager != null) manager.clearCommunicationDevice();
        }
    }
}
