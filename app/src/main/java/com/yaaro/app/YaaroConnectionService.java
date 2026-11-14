package com.yaaro.app;

import android.os.Bundle;
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
        Log.i(TAG, "Incoming Telecom connection requested; audio integration pending");
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
