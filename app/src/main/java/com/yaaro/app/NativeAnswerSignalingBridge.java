package com.yaaro.app;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges Firebase call signaling to an already-prepared native peer.
 * The owning Activity/service must establish authorized microphone access,
 * prepareAnswer(), and createObserver() before starting this bridge.
 * This class never starts a service or answers notification actions.
 */
public final class NativeAnswerSignalingBridge {
    public interface Listener {
        void onAnswerPublished();
        void onConnected();
        void onDisconnected();
        void onEnded(String status);
        void onFailure(String reason);
    }

    private final NativeVoiceCallCoordinator coordinator;
    private final String callId;
    private final String localUid;
    private final String remoteUid;
    private final Listener listener;
    private static final int MAX_BUFFERED_ICE = 256;
    private boolean negotiationStarted;
    private boolean stopped;
    private boolean remoteDescriptionReady;
    private boolean signalingReady;
    private boolean terminalCallStatus;
    private final List<IceCandidate> pendingRemoteIce = new ArrayList<>();
    private final List<IceCandidate> pendingLocalIce = new ArrayList<>();

    public NativeAnswerSignalingBridge(NativeVoiceCallCoordinator coordinator,
            String callId, String localUid, String remoteUid, Listener listener) {
        if (coordinator == null || listener == null || callId == null ||
                callId.isEmpty() || localUid == null || localUid.isEmpty() ||
                remoteUid == null || remoteUid.isEmpty()) {
            throw new IllegalArgumentException("Call and participant IDs required");
        }
        this.coordinator = coordinator;
        this.callId = callId;
        this.localUid = localUid;
        this.remoteUid = remoteUid;
        this.listener = listener;
    }

    public synchronized NativePeerConnectionObserver createObserver() {
        return coordinator.createObserver(callId, localUid,
                new NativePeerConnectionObserver.Listener() {
                    @Override public void onLocalIceCandidate(IceCandidate candidate) {
                        if (stopped) return;
                        synchronized (NativeAnswerSignalingBridge.this) {
                            if (!signalingReady) {
                                if (pendingLocalIce.size() >= MAX_BUFFERED_ICE) {
                                    listener.onFailure("Local ICE buffer limit exceeded");
                                    return;
                                }
                                pendingLocalIce.add(candidate);
                                return;
                            }
                        }
                        try {
                            coordinator.publishLocalIce(callId, localUid, candidate);
                        } catch (RuntimeException error) {
                            listener.onFailure("Local ICE: " + error.getMessage());
                        }
                    }
                    @Override public void onConnected() {
                        if (!stopped) listener.onConnected();
                    }
                    @Override public void onDisconnected() {
                        if (!stopped) listener.onDisconnected();
                    }
                    @Override public void onFailed() {
                        if (!stopped) listener.onFailure("Peer connection failed");
                    }
                });
    }

    /** Call only after coordinator.prepareAnswer() successfully returns. */
    public synchronized void start() {
        if (stopped || negotiationStarted) throw new IllegalStateException("Bridge already used");
        NativeCallSignaling.Listener signalingListener = new NativeCallSignaling.Listener() {
            @Override public void onOffer(String type, String sdp) {
                synchronized (NativeAnswerSignalingBridge.this) {
                    if (stopped || terminalCallStatus || negotiationStarted) return;
                    negotiationStarted = true;
                }
                try {
                    coordinator.negotiateAnswer(callId, sdp, new NativeAnswerNegotiator.Listener() {
                        @Override public void onLocalAnswer(SessionDescription answer) {
                            if (stopped || terminalCallStatus) return;
                            // setLocalDescription completed, so remote offer is installed.
                            flushRemoteIce();
                            coordinator.publishAnswer(callId, answer,
                                    (DatabaseError error, DatabaseReference ref) -> {
                                        if (stopped || terminalCallStatus) return;
                                        if (error != null) listener.onFailure("Answer write: " + error.getMessage());
                                        else listener.onAnswerPublished();
                                    });
                        }
                        @Override public void onFailure(String reason) {
                            if (!stopped) listener.onFailure(reason);
                        }
                    });
                } catch (RuntimeException error) {
                    listener.onFailure("Negotiation: " + error.getMessage());
                }
            }
            @Override public void onCallStatus(String status) {
                if ("ended".equals(status) || "cancelled".equals(status) ||
                        "rejected".equals(status) || "missed".equals(status) ||
                        "busy".equals(status)) {
                    synchronized (NativeAnswerSignalingBridge.this) {
                        if (stopped || terminalCallStatus) return;
                        terminalCallStatus = true;
                    }
                    stop();
                    coordinator.end(callId);
                    listener.onEnded(status);
                }
            }
            @Override public void onRemoteIceCandidate(IceCandidate candidate) {
                synchronized (NativeAnswerSignalingBridge.this) {
                    if (stopped || terminalCallStatus) return;
                    if (!remoteDescriptionReady) {
                        if (pendingRemoteIce.size() >= MAX_BUFFERED_ICE) {
                            listener.onFailure("Remote ICE buffer limit exceeded");
                            return;
                        }
                        pendingRemoteIce.add(candidate);
                        return;
                    }
                }
                applyRemoteIce(candidate);
            }
            @Override public void onError(DatabaseError error) {
                if (!stopped) listener.onFailure("Signaling: " + error.getMessage());
            }
        };
        coordinator.observeSignaling(callId, localUid, signalingListener);
        coordinator.observeRemoteIce(callId, remoteUid, signalingListener);
        List<IceCandidate> buffered;
        synchronized (this) {
            signalingReady = true;
            buffered = new ArrayList<>(pendingLocalIce);
            pendingLocalIce.clear();
        }
        for (IceCandidate candidate : buffered) {
            if (stopped) break;
            try { coordinator.publishLocalIce(callId, localUid, candidate); }
            catch (RuntimeException error) { listener.onFailure("Local ICE: " + error.getMessage()); }
        }
    }

    private void applyRemoteIce(IceCandidate candidate) {
        try {
            if (!coordinator.addRemoteIce(callId, candidate)) {
                listener.onFailure("Remote ICE could not be applied");
            }
        } catch (RuntimeException error) {
            listener.onFailure("Remote ICE: " + error.getMessage());
        }
    }

    private void flushRemoteIce() {
        List<IceCandidate> buffered;
        synchronized (this) {
            if (stopped || terminalCallStatus) return;
            remoteDescriptionReady = true;
            buffered = new ArrayList<>(pendingRemoteIce);
            pendingRemoteIce.clear();
        }
        for (IceCandidate candidate : buffered) {
            if (stopped) break;
            applyRemoteIce(candidate);
        }
    }

    public synchronized void stop() {
        stopped = true;
        pendingRemoteIce.clear();
        pendingLocalIce.clear();
    }
}
