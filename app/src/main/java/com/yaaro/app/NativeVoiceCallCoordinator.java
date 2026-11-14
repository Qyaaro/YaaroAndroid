package com.yaaro.app;

import android.content.Context;

import org.webrtc.PeerConnection;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import com.google.firebase.database.DatabaseReference;

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
    private final NativeCallSignaling signaling = new NativeCallSignaling();
    private PeerConnection activePeer;

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
            activePeer = engine.createAudioConnection(rtcConfig, observer);
            return activePeer;
        } catch (RuntimeException | Error error) {
            activePeer = null;
            engine.closeCall();
            session.end(callId);
            throw error;
        }
    }

    /**
     * Must be called after the microphone permission and foreground-service
     * eligibility checks have passed. The caller supplies a validated offer.
     * Publishing the answer happens only after setLocalDescription succeeds.
     */
    public synchronized void negotiateAnswer(String callId, String offerSdp,
            NativeAnswerNegotiator.Listener listener) {
        if (activePeer == null || callId == null ||
                !callId.equals(session.getCallId()) ||
                session.getState() != NativeCallSession.State.CONNECTING) {
            throw new IllegalStateException("No matching prepared call");
        }
        NativeAnswerNegotiator.answer(activePeer, offerSdp, listener);
    }

    public synchronized void observeSignaling(String callId, NativeCallSignaling.Listener listener) {
        if (callId == null || !callId.equals(session.getCallId())) {
            throw new IllegalStateException("Call ID mismatch");
        }
        signaling.observe(callId, listener);
    }

    public synchronized void observeRemoteIce(String callId, String remoteUid,
            NativeCallSignaling.Listener listener) {
        signaling.observeRemoteCandidates(callId, remoteUid, listener);
    }

    public synchronized boolean addRemoteIce(String callId, IceCandidate candidate) {
        return activePeer != null && callId != null &&
                callId.equals(session.getCallId()) && candidate != null &&
                activePeer.addIceCandidate(candidate);
    }

    public synchronized void publishAnswer(String callId, SessionDescription answer,
            DatabaseReference.CompletionListener completion) {
        signaling.publishAnswer(callId, answer, completion);
    }

    public synchronized void publishLocalIce(String callId, String localUid,
            IceCandidate candidate) {
        signaling.publishIceCandidate(callId, localUid, candidate);
    }

    /** Call only when WebRTC confirms a connected peer, not merely on answer SDP. */
    public synchronized boolean markConnected(String callId) {
        if (!engine.hasActiveConnection()) return false;
        return session.connected(callId);
    }

    public synchronized boolean end(String callId) {
        if (!session.end(callId)) return false;
        signaling.stop();
        activePeer = null;
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
        signaling.stop();
        activePeer = null;
        engine.release();
        String callId = session.getCallId();
        if (callId != null) session.end(callId);
    }
}
