
package com.yaaro.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CallActionReceiver extends BroadcastReceiver {

    private static final String WORKER_URL =
        "https://yaaro-call-push.aryan3765955.workers.dev/";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null ||
            !YaaroFirebaseMessagingService.ACTION_DECLINE
                .equals(intent.getAction())) {
            return;
        }

        final String callId = intent.getStringExtra(
            YaaroFirebaseMessagingService.EXTRA_CALL_ID
        );

        if (callId == null || callId.isEmpty()) return;

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();

        YaaroRingtonePlayer.stop();

        new Thread(() -> {
            try {
                SharedPreferences prefs =
                    appContext.getSharedPreferences(
                        "yaaro", Context.MODE_PRIVATE
                    );

                String token = prefs.getString(
                    "firebase_id_token", ""
                );

                String uid = prefs.getString(
                    "firebase_uid", ""
                );

                if (token == null || token.isEmpty() ||
                    uid == null || uid.isEmpty()) {
                    Log.e("YaaroDecline", "Missing login session");
                    return;
                }

                JSONObject body = new JSONObject();
                body.put("action", "decline");
                body.put("callId", callId);
                body.put("toUid", uid);

                HttpURLConnection connection =
                    (HttpURLConnection)
                        new URL(WORKER_URL).openConnection();

                try {
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setDoOutput(true);

                    connection.setRequestProperty(
                        "Content-Type", "application/json"
                    );

                    connection.setRequestProperty(
                        "Authorization", "Bearer " + token
                    );

                    byte[] data = body.toString().getBytes(
                        StandardCharsets.UTF_8
                    );

                    try (OutputStream output =
                             connection.getOutputStream()) {
                        output.write(data);
                    }

                    int response = connection.getResponseCode();

                    if (response >= 200 && response < 300) {
                        YaaroFirebaseMessagingService
                            .cancelCallNotification(
                                appContext, callId
                            );

                        Log.d("YaaroDecline", "Call rejected");
                    } else {
                        Log.e(
                            "YaaroDecline",
                            "Worker response: " + response
                        );
                    }

                } finally {
                    connection.disconnect();
                }

            } catch (Exception e) {
                Log.e("YaaroDecline", "Decline failed", e);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
