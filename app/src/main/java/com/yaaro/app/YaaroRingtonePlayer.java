package com.yaaro.app;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

public class YaaroRingtonePlayer {

    private static Ringtone ringtone;

    public static synchronized void start(Context context) {
        stop();

        try {
            Uri uri = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_RINGTONE
            );

            ringtone = RingtoneManager.getRingtone(
                    context.getApplicationContext(), uri
            );

            if (ringtone != null) {
    if (android.os.Build.VERSION.SDK_INT >= 21) {
        android.media.AudioAttributes attributes =
                new android.media.AudioAttributes.Builder()
                        .setUsage(
                            android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                        )
                        .setContentType(
                            android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build();

        ringtone.setAudioAttributes(attributes);
    }

    ringtone.play();

    android.util.Log.d(
        "YaaroRingtone",
        "Ringtone play requested: " + ringtone.isPlaying()
    );
}
                
        } catch (Exception e) {
            android.util.Log.e(
                    "YaaroRingtone",
                    "Ringtone start failed",
                    e
            );
        }
    }

    public static synchronized void stop() {
        if (ringtone != null) {
            try {
                ringtone.stop();
            } catch (Exception e) {
                android.util.Log.e(
                        "YaaroRingtone",
                        "Ringtone stop failed",
                        e
                );
            }
            ringtone = null;
        }
    }
}
