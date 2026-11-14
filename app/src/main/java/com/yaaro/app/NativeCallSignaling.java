package com.yaaro.app;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Read-only native signaling observer for the existing calls/{callId} schema.
 * Never writes an answer or accepts a call until a verified native call flow
 * and microphone foreground-service permissions are integrated.
 */
public final class NativeCallSignaling {
    public interface Listener {
        void onOffer(String type, String sdp);
        void onCallStatus(String status);
        void onError(DatabaseError error);
    }

    private DatabaseReference callRef;
    private ValueEventListener callListener;

    public synchronized void observe(String callId, Listener listener) {
        if (callId == null || callId.isEmpty() || listener == null) {
            throw new IllegalArgumentException("Call ID and listener required");
        }
        stop();
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

    public synchronized void stop() {
        if (callRef != null && callListener != null) {
            callRef.removeEventListener(callListener);
        }
        callRef = null;
        callListener = null;
    }
}
