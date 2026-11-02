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
                ringtone.play();
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
