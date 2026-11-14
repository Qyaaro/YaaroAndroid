package com.yaaro.app;

import android.content.Context;

import org.webrtc.PeerConnection;

/**
 * Owns the lifecycle of one native voice call. Signaling and Android microphone
 * permission / foreground service must be supplied by the caller.
 *
 * Do not invoke this from a background notification until the Android
 * microphone foreground-service eligibility checks have succeeded.
 */
public final class NativeVoiceCallCoordinator {
    private final NativeCallSession session = new NativeCallSession();
    private final NativeWebRtcAudioEngine engine = new NativeWebRtcAudioEngine();

    public synchronized boolean incoming(String callId) {
        return session.incoming(callId);
    }

    public synchronized PeerConnection prepareAnswer(
            Context context, String callId,
            PeerConnection.RTCConfiguration rtcConfig,
            PeerConnection.Observer observer) {
        if (!session.beginAnswer(callId)) {
            throw new IllegalStateException("Call not ringing or ID mismatch");
        }
        try {
            engine.initialize(context);
            return engine.createAudioConnection(rtcConfig, observer);
        } catch (RuntimeException | Error error) {
            engine.closeCall();
            session.end(callId);
            throw error;
        }
    }

    /** Call only when WebRTC confirms a connected peer, not merely on answer SDP. */
    public synchronized boolean markConnected(String callId) {
        if (!engine.hasActiveConnection()) return false;
        return session.connected(callId);
    }

    public synchronized boolean end(String callId) {
        if (!session.end(callId)) return false;
        engine.closeCall();
        return true;
    }

    public synchronized String getDisplayStatus() {
        return session.getDisplayStatus();
    }

    public synchronized String getCallId() {
        return session.getCallId();
    }

    public synchronized void release() {
        engine.release();
        String callId = session.getCallId();
        if (callId != null) session.end(callId);
    }
}
