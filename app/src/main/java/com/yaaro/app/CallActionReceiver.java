
package com.yaaro.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;

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
            // Preflight only: verify the authenticated receiver and the live
            // ringing voice call before attempting native answer integration.
            // Never launch an Activity, publish "accepted", or start the
            // microphone from this receiver (Android 14 while-in-use limits).
            final PendingResult acceptResult = goAsync();
            FirebaseUser receiver = FirebaseAuth.getInstance().getCurrentUser();
            if (receiver == null || !receiver.isEmailVerified()) {
                Log.w("YaaroAccept", "Verified native Firebase session required");
                acceptResult.finish();
                return;
            }
            final String receiverUid = receiver.getUid();
            try {
                FirebaseDatabase.getInstance().getReference("calls").child(callId)
                    .get().addOnCompleteListener(task -> {
                        try {
                            if (!task.isSuccessful() || task.getResult() == null) {
                                Log.w("YaaroAccept", "Call verification failed");
                                return;
                            }
                            // The account may have changed while Firebase was loading.
                            // Never authorize an answer for a previous signed-in user.
                            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                            if (current == null || !current.isEmailVerified() ||
                                    !receiverUid.equals(current.getUid())) {
                                Log.w("YaaroAccept", "Native receiver changed during preflight");
                                return;
                            }
                            DataSnapshot call = task.getResult();
                            String to = call.child("to").getValue(String.class);
                            String type = call.child("type").getValue(String.class);
                            String status = call.child("status").getValue(String.class);
                            String callerUid = call.child("from").getValue(String.class);
                            String offerType = call.child("offer").child("type").getValue(String.class);
                            String offerSdp = call.child("offer").child("sdp").getValue(String.class);
                            if (!receiverUid.equals(to) || !"voice".equals(type) ||
                                    !"ringing".equals(status) ||
                                    callerUid == null || callerUid.trim().isEmpty() ||
                                    receiverUid.equals(callerUid)) {
                                Log.w("YaaroAccept", "Not an eligible ringing voice call");
                                return;
                            }
                            // A native WebRTC answer requires a genuine caller SDP offer.
                            // Do not claim success or attempt microphone capture if absent.
                            if (!"offer".equals(offerType) || offerSdp == null ||
                                    offerSdp.trim().isEmpty()) {
                                Log.w("YaaroAccept", "Caller WebRTC offer not ready");
                                return;
                            }
                            if (!NativeVoiceAudioPolicy.hasMicrophonePermission(
                                    context.getApplicationContext())) {
                                Log.w("YaaroAccept", "RECORD_AUDIO permission not granted");
                                return;
                            }
                            Log.i("YaaroAccept",
                                    "Verified receiver, ringing call, caller offer and microphone permission; " +
                                    "background audio service integration pending");
                        } finally {
                            acceptResult.finish();
                        }
                    });
            } catch (RuntimeException error) {
                Log.e("YaaroAccept", "Call preflight failed", error);
                acceptResult.finish();
            }
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
