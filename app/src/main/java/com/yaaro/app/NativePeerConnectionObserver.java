package com.yaaro.app;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

/**
 * Maps actual WebRTC transport events to native call state.
 * A prepared peer connection alone must never show Connected.
 */
public final class NativePeerConnectionObserver implements PeerConnection.Observer {
    public interface Listener {
        void onLocalIceCandidate(IceCandidate candidate);
        void onConnected();
        void onDisconnected();
        void onFailed();
    }

    private final Listener listener;
    private boolean connected;

    public NativePeerConnectionObserver(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("Listener required");
        this.listener = listener;
    }

    @Override public void onIceCandidate(IceCandidate candidate) {
        if (candidate != null) listener.onLocalIceCandidate(candidate);
    }

    @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
        switch (state) {
            case CONNECTED:
                if (!connected) {
                    connected = true;
                    listener.onConnected();
                }
                break;
            case DISCONNECTED:
                connected = false;
                listener.onDisconnected();
                break;
            case FAILED:
            case CLOSED:
                connected = false;
                listener.onFailed();
                break;
            default:
                break;
        }
    }

    @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
    @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) { }
    @Override public void onIceConnectionReceivingChange(boolean receiving) { }
    @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
    @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
    @Override public void onAddStream(MediaStream stream) { }
    @Override public void onRemoveStream(MediaStream stream) { }
    @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
    @Override public void onRenegotiationNeeded() { }
    @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) { }
}
