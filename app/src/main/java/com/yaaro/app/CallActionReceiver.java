
package com.yaaro.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CallActionReceiver extends BroadcastReceiver {

    private static final String WORKER_URL =
        "https://yaaro-call-push.aryan3765955.workers.dev/";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) return;
        final String action = intent.getAction();
        if (!YaaroFirebaseMessagingService.ACTION_DECLINE.equals(action) &&
                !YaaroFirebaseMessagingService.ACTION_ACCEPT.equals(action)) return;

        final String callId = intent.getStringExtra(
            YaaroFirebaseMessagingService.EXTRA_CALL_ID
        );
        if (callId == null || callId.trim().isEmpty()) return;

        if (YaaroFirebaseMessagingService.ACTION_ACCEPT.equals(action)) {
            // Never launch an Activity or claim an accepted/connected call here.
            // Background WebRTC and microphone foreground-service eligibility
            // must be established before publishing an answer or changing status.
            // Leave the incoming notification intact until that integration exists.
            Log.w("YaaroAccept", "Native background answer not yet available");
            return;
        }

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();

        // A notification action must respond immediately without opening the app.
        // Clear the local alert even if the network request is delayed or fails.
        YaaroRingtonePlayer.stop();
        YaaroFirebaseMessagingService.cancelCallNotification(appContext, callId);

        // Use the native Firebase session, not an expired WebView-cached ID token.
        // This is asynchronous and does not launch or unlock the Activity.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            Log.w("YaaroDecline", "No verified native Firebase session");
            pendingResult.finish();
            return;
        }
        final String receiverUid = user.getUid();
        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null ||
                    task.getResult().getToken() == null) {
                Log.e("YaaroDecline", "Unable to obtain native Firebase ID token");
                pendingResult.finish();
                return;
            }
            final String token = task.getResult().getToken();
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("action", "decline");
                    body.put("callId", callId);
                    body.put("toUid", receiverUid);

                    HttpURLConnection connection =
                        (HttpURLConnection) new URL(WORKER_URL).openConnection();
                    try {
                        connection.setRequestMethod("POST");
                        connection.setConnectTimeout(4000);
                        connection.setReadTimeout(4000);
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/json");
                        connection.setRequestProperty("Authorization", "Bearer " + token);
                        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                        try (OutputStream output = connection.getOutputStream()) {
                            output.write(data);
                        }
                        int response = connection.getResponseCode();
                        if (response >= 200 && response < 300) {
                            Log.d("YaaroDecline", "Call rejected");
                        } else {
                            Log.e("YaaroDecline", "Worker response: " + response);
                        }
                    } finally {
                        connection.disconnect();
                    }
                } catch (Exception e) {
                    Log.e("YaaroDecline", "Decline failed", e);
                } finally {
                    pendingResult.finish();
                }
            }, "YaaroDeclineWorker").start();
        });
    }
}
