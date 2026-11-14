package com.yaaro.app;

import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

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
        Connection connection = new Connection() {
            @Override public void onAnswer() {
                // Never claim an active call without an authorized WebRTC session.
                setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
                destroy();
            }
            @Override public void onReject() {
                setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                destroy();
            }
            @Override public void onDisconnect() {
                setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                destroy();
            }
        };
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        connection.setExtras(incoming);
        connection.setInitializing();
        connection.setRinging();
        return connection;
    }

    @Override
    public void onCreateIncomingConnectionFailed(
            PhoneAccountHandle handle, ConnectionRequest request) {
        Log.w(TAG, "Telecom denied incoming connection");
    }
}
