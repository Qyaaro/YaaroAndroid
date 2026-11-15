package com.yaaro.app;

import android.os.Bundle;
import android.content.Intent;
import android.telecom.TelecomManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Telecom entry point for future self-managed voice calls.
 * This does not start WebRTC or microphone capture. Telecom must own the
 * incoming-call lifecycle before background/locked-device answering is enabled.
 */
public final class YaaroConnectionService extends ConnectionService {
    private static final String TAG = "YaaroTelecom";

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle handle, ConnectionRequest request) {
        // Only accept requests addressed to QYAARO's registered phone account.
        if (handle == null || !YaaroTelecomAccount.handle(this).equals(handle)) {
            Log.w(TAG, "Rejecting unknown Telecom phone account");
            return null;
        }
        Bundle extras = request == null ? null : request.getExtras();
        Bundle incoming = extras == null ? null :
                extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
        // Some Android versions pass the incoming extras directly.
        if (incoming == null) incoming = extras;
        final String callId = incoming == null ? null :
                incoming.getString("com.yaaro.app.CALL_ID");
        if (callId == null || callId.trim().isEmpty()) {
            Log.w(TAG, "Rejecting Telecom request without call ID");
            return null;
        }
        Log.i(TAG, "Incoming Telecom connection created; WebRTC audio pending");
        final AtomicBoolean closed = new AtomicBoolean(false);
        final AtomicBoolean verifiedRinging = new AtomicBoolean(false);
        final DatabaseReference statusRef = FirebaseDatabase.getInstance()
                .getReference("calls").child(callId).child("status");
        final AtomicReference<ValueEventListener> statusObserver = new AtomicReference<>();
        final Runnable stopWatching = () -> {
            ValueEventListener observer = statusObserver.getAndSet(null);
            if (observer != null) statusRef.removeEventListener(observer);
        };
        Connection connection = new Connection() {
            @Override public void onAnswer() {
                // Route Telecom's answer request through the same authenticated
                // Firebase/SDP/microphone preflight as notification Accept.
                // This receiver does not start microphone capture or open UI.
                if (!closed.compareAndSet(false, true)) return;
                stopWatching.run();
                if (!verifiedRinging.get()) {
                    Log.w(TAG, "Telecom Answer denied before Firebase verification");
                    setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
                    destroy();
                    return;
                }
                YaaroRingtonePlayer.stop();
                YaaroFirebaseMessagingService.cancelCallNotification(
                        getApplicationContext(), callId);
                Intent accept = new Intent(getApplicationContext(), CallActionReceiver.class);
                accept.setAction(YaaroFirebaseMessagingService.ACTION_ACCEPT);
                accept.putExtra(YaaroFirebaseMessagingService.EXTRA_CALL_ID, callId);
                getApplicationContext().sendBroadcast(accept);
                // Do not report ACTIVE: no authorized WebRTC peer is running yet.
                setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
                destroy();
            }
            @Override public void onReject() {
                if (!closed.compareAndSet(false, true)) return;
                stopWatching.run();
                YaaroRingtonePlayer.stop();
                YaaroFirebaseMessagingService.cancelCallNotification(
                        getApplicationContext(), callId);
                // Reuse the authenticated Worker decline flow. The explicit
                // in-app broadcast never launches MainActivity.
                Intent decline = new Intent(getApplicationContext(), CallActionReceiver.class);
                decline.setAction(YaaroFirebaseMessagingService.ACTION_DECLINE);
                decline.putExtra(YaaroFirebaseMessagingService.EXTRA_CALL_ID, callId);
                getApplicationContext().sendBroadcast(decline);
                setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                destroy();
            }
            @Override public void onDisconnect() {
                if (!closed.compareAndSet(false, true)) return;
                stopWatching.run();
                YaaroRingtonePlayer.stop();
                YaaroFirebaseMessagingService.cancelCallNotification(
                        getApplicationContext(), callId);
                setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                destroy();
            }
        };
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        connection.setExtras(incoming);
        connection.setInitializing();
        // Telecom request extras are not proof of Firebase call ownership.
        // Keep the connection initializing until the current native user and
        // ringing voice call have been checked against Firebase.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            closed.set(true);
            stopWatching.run();
            connection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
            connection.destroy();
            return connection;
        }
        final String receiverUid = user.getUid();
        FirebaseDatabase.getInstance().getReference("calls").child(callId)
                .get().addOnCompleteListener(task -> {
                    // An asynchronous Firebase response must never resurrect a
                    // connection already rejected, answered, or disconnected.
                    if (closed.get()) return;
                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                    if (current == null || !receiverUid.equals(current.getUid()) ||
                            !task.isSuccessful() || task.getResult() == null) {
                        closed.set(true);
                        stopWatching.run();
                        connection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
                        connection.destroy();
                        return;
                    }
                    DataSnapshot call = task.getResult();
                    if (!receiverUid.equals(call.child("to").getValue(String.class)) ||
                            !"voice".equals(call.child("type").getValue(String.class)) ||
                            !"ringing".equals(call.child("status").getValue(String.class))) {
                        closed.set(true);
                        stopWatching.run();
                        connection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
                        connection.destroy();
                        return;
                    }
                    if (closed.get()) return;
                    ValueEventListener observer = new ValueEventListener() {
                        @Override public void onDataChange(DataSnapshot snapshot) {
                            String status = snapshot.getValue(String.class);
                            if ("ringing".equals(status) || closed.get()) return;
                            if (!closed.compareAndSet(false, true)) return;
                            stopWatching.run();
                            YaaroRingtonePlayer.stop();
                            YaaroFirebaseMessagingService.cancelCallNotification(
                                    getApplicationContext(), callId);
                            connection.setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
                            connection.destroy();
                        }
                        @Override public void onCancelled(DatabaseError error) {
                            Log.w(TAG, "Telecom status observer cancelled", error.toException());
                            if (!closed.compareAndSet(false, true)) return;
                            stopWatching.run();
                            YaaroRingtonePlayer.stop();
                            YaaroFirebaseMessagingService.cancelCallNotification(
                                    getApplicationContext(), callId);
                            connection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
                            connection.destroy();
                        }
                    };
                    statusObserver.set(observer);
                    statusRef.addValueEventListener(observer);
                    if (closed.get()) {
                        stopWatching.run();
                        return;
                    }
                    verifiedRinging.set(true);
                    if (!closed.get()) connection.setRinging();
                });
        return connection;
    }

    @Override
    public void onCreateIncomingConnectionFailed(
            PhoneAccountHandle handle, ConnectionRequest request) {
        Log.w(TAG, "Telecom denied incoming connection");
    }
}
