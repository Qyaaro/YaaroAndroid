package com.yaaro.app;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import java.util.HashMap;
import java.util.Map;
import com.google.firebase.database.ValueEventListener;

/**
 * Native signaling adapter for the existing calls/{callId} schema.
 * Publishing requires explicit invocation from a verified native call flow;
 * this class does not auto-answer or start microphone capture.
 */
public final class NativeCallSignaling {
    public interface Listener {
        void onOffer(String type, String sdp);
        void onCallStatus(String status);
        void onRemoteIceCandidate(IceCandidate candidate);
        void onError(DatabaseError error);
    }

    private DatabaseReference callRef;
    private ValueEventListener callListener;
    private DatabaseReference remoteCandidatesRef;
    private ChildEventListener candidatesListener;
    private String activeCallId;

    public synchronized void observe(String callId, Listener listener) {
        if (callId == null || callId.isEmpty() || listener == null) {
            throw new IllegalArgumentException("Call ID and listener required");
        }
        stop();
        activeCallId = callId;
        callRef = FirebaseDatabase.getInstance().getReference("calls").child(callId);
        callListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if (status != null) listener.onCallStatus(status);
                String type = snapshot.child("offer").child("type").getValue(String.class);
                String sdp = snapshot.child("offer").child("sdp").getValue(String.class);
                if ("offer".equals(type) && sdp != null && !sdp.isEmpty()) {
                    listener.onOffer(type, sdp);
                }
            }
            @Override public void onCancelled(DatabaseError error) {
                listener.onError(error);
            }
        };
        callRef.addValueEventListener(callListener);
    }

    /** Observe remote ICE candidates from the existing web calling schema. */
    public synchronized void observeRemoteCandidates(String callId, String remoteUid, Listener listener) {
        if (callRef == null || !callId.equals(activeCallId) ||
                remoteUid == null || remoteUid.isEmpty() || listener == null) {
            throw new IllegalArgumentException("Observe the call before remote ICE");
        }
        detachCandidates();
        remoteCandidatesRef = callRef.child("candidates").child(remoteUid);
        candidatesListener = new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String mid = snapshot.child("sdpMid").getValue(String.class);
                Integer index = snapshot.child("sdpMLineIndex").getValue(Integer.class);
                String candidate = snapshot.child("candidate").getValue(String.class);
                if (candidate != null && index != null) {
                    listener.onRemoteIceCandidate(new IceCandidate(mid, index, candidate));
                }
            }
            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) { }
            @Override public void onChildRemoved(DataSnapshot snapshot) { }
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) { }
            @Override public void onCancelled(DatabaseError error) { listener.onError(error); }
        };
        remoteCandidatesRef.addChildEventListener(candidatesListener);
    }

    /** Writes SDP answer only when an authorized native answer flow invokes it. */
    public synchronized void publishAnswer(String callId, SessionDescription answer, CompletionListener completion) {
        requireActive(callId);
        if (answer == null || answer.type != SessionDescription.Type.ANSWER) {
            throw new IllegalArgumentException("Valid answer SDP required");
        }
        Map<String, Object> value = new HashMap<>();
        value.put("type", "answer");
        value.put("sdp", answer.description);
        callRef.child("answer").setValue(value, completion);
    }

    /** Publish local ICE using calls/{callId}/candidates/{localUid}. */
    public synchronized void publishIceCandidate(String callId, String localUid, IceCandidate candidate) {
        requireActive(callId);
        if (localUid == null || localUid.isEmpty() || candidate == null) {
            throw new IllegalArgumentException("Local UID and candidate required");
        }
        Map<String, Object> value = new HashMap<>();
        value.put("candidate", candidate.sdp);
        value.put("sdpMid", candidate.sdpMid);
        value.put("sdpMLineIndex", candidate.sdpMLineIndex);
        callRef.child("candidates").child(localUid).push().setValue(value);
    }

    private void requireActive(String callId) {
        if (callRef == null || callId == null || !callId.equals(activeCallId)) {
            throw new IllegalStateException("Signaling call ID mismatch");
        }
    }

    private void detachCandidates() {
        if (remoteCandidatesRef != null && candidatesListener != null) {
            remoteCandidatesRef.removeEventListener(candidatesListener);
        }
        remoteCandidatesRef = null;
        candidatesListener = null;
    }

    public synchronized void stop() {
        detachCandidates();
        if (callRef != null && callListener != null) {
            callRef.removeEventListener(callListener);
        }
        callRef = null;
        callListener = null;
        activeCallId = null;
    }
}
