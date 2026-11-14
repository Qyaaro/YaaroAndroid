package com.yaaro.app;

import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/**
 * Explicitly invoked SDP answer sequence for an already-authorized native
 * microphone session. Does not launch the app or start background recording.
 */
public final class NativeAnswerNegotiator {
    public interface Listener {
        void onLocalAnswer(SessionDescription answer);
        void onFailure(String reason);
    }

    private NativeAnswerNegotiator() { }

    public static void answer(PeerConnection peer,
                              String remoteOfferSdp,
                              Listener listener) {
        if (peer == null || remoteOfferSdp == null ||
                remoteOfferSdp.isEmpty() || listener == null) {
            throw new IllegalArgumentException("Peer, offer and listener required");
        }
        SessionDescription offer =
                new SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp);
        peer.setRemoteDescription(new SdpObserver() {
            @Override public void onSetSuccess() {
                peer.createAnswer(new SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription answer) {
                        peer.setLocalDescription(new SdpObserver() {
                            @Override public void onSetSuccess() {
                                listener.onLocalAnswer(answer);
                            }
                            @Override public void onSetFailure(String error) {
                                listener.onFailure("setLocalDescription: " + error);
                            }
                            @Override public void onCreateSuccess(SessionDescription ignored) { }
                            @Override public void onCreateFailure(String error) {
                                listener.onFailure(error);
                            }
                        }, answer);
                    }
                    @Override public void onCreateFailure(String error) {
                        listener.onFailure("createAnswer: " + error);
                    }
                    @Override public void onSetSuccess() { }
                    @Override public void onSetFailure(String error) {
                        listener.onFailure(error);
                    }
                }, new org.webrtc.MediaConstraints());
            }
            @Override public void onSetFailure(String error) {
                listener.onFailure("setRemoteDescription: " + error);
            }
            @Override public void onCreateSuccess(SessionDescription ignored) { }
            @Override public void onCreateFailure(String error) {
                listener.onFailure(error);
            }
        }, offer);
    }
}
