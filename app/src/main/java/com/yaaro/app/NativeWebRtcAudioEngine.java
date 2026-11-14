package com.yaaro.app;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.JavaAudioDeviceModule;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;

import java.util.Collections;

/**
 * Native WebRTC audio resources for a single call.
 *
 * This class does not accept calls, start services, or read Firebase on its own.
 * The caller must first establish a legal microphone foreground-service context
 * and verify RECORD_AUDIO permission. Signaling will be integrated separately.
 */
public final class NativeWebRtcAudioEngine {
    private static final String TAG = "YaaroWebRTC";
    private static final String AUDIO_TRACK_ID = "yaaro-native-audio";
    private static final String STREAM_ID = "yaaro-native-stream";

    private PeerConnectionFactory factory;
    private JavaAudioDeviceModule audioModule;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private PeerConnection peerConnection;

    public synchronized void initialize(Context context) {
        if (factory != null) return;
        Context app = context.getApplicationContext();
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(app)
                        .createInitializationOptions());
        audioModule = JavaAudioDeviceModule.builder(app).createAudioDeviceModule();
        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioModule)
                .createPeerConnectionFactory();
        if (factory == null) {
            audioModule.release();
            audioModule = null;
            throw new IllegalStateException("WebRTC factory unavailable");
        }
        Log.d(TAG, "Native WebRTC factory initialized");
    }

    /**
     * Create one audio-only peer connection after foreground microphone access
     * has been authorized. A signaling owner must supply ICE servers/observer.
     */
    public synchronized PeerConnection createAudioConnection(
            PeerConnection.RTCConfiguration config,
            PeerConnection.Observer observer) {
        if (factory == null) throw new IllegalStateException("Initialize first");
        if (peerConnection != null) throw new IllegalStateException("Call already active");
        if (config == null || observer == null) {
            throw new IllegalArgumentException("RTC config and observer required");
        }
        PeerConnection pc = factory.createPeerConnection(config, observer);
        if (pc == null) throw new IllegalStateException("Peer connection unavailable");
        try {
            AudioSource source = factory.createAudioSource(new MediaConstraints());
            AudioTrack track = factory.createAudioTrack(AUDIO_TRACK_ID, source);
            track.setEnabled(true);
            if (pc.addTrack(track, Collections.singletonList(STREAM_ID)) == null) {
                track.dispose();
                source.dispose();
                throw new IllegalStateException("Could not add microphone track");
            }
            audioSource = source;
            localAudioTrack = track;
            peerConnection = pc;
            return pc;
        } catch (RuntimeException e) {
            pc.dispose();
            throw e;
        }
    }

    public synchronized void closeCall() {
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }
        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
    }

    public synchronized void release() {
        closeCall();
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        if (audioModule != null) {
            audioModule.release();
            audioModule = null;
        }
    }
}
