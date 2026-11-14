package com.yaaro.app;

/**
 * Thread-safe lifecycle for one native call. No Firebase or WebRTC side effects:
 * those will be wired only after the native audio engine is implemented.
 */
public final class NativeCallSession {
    public enum State { IDLE, RINGING, CONNECTING, CONNECTED, ENDED }

    private String callId;
    private State state = State.IDLE;

    public synchronized boolean incoming(String incomingCallId) {
        if (incomingCallId == null || incomingCallId.isEmpty()
                || (state != State.IDLE && state != State.ENDED)) return false;
        callId = incomingCallId;
        state = State.RINGING;
        return true;
    }

    public synchronized boolean beginAnswer(String expectedCallId) {
        if (!matches(expectedCallId) || state != State.RINGING) return false;
        state = State.CONNECTING;
        return true;
    }

    public synchronized boolean connected(String expectedCallId) {
        if (!matches(expectedCallId) || state != State.CONNECTING) return false;
        state = State.CONNECTED;
        return true;
    }

    public synchronized boolean end(String expectedCallId) {
        if (!matches(expectedCallId) || state == State.ENDED) return false;
        state = State.ENDED;
        return true;
    }

    /**
     * Public call status for future UI rendering.
     * CONNECTING remains internal: keep the ringing display until media
     * is actually connected. Never label an unconnected call CONNECTED.
     */
    public synchronized String getDisplayStatus() {
        switch (state) {
            case RINGING:
            case CONNECTING:
                return "Ringing";
            case CONNECTED:
                return "Connected";
            case ENDED:
                return "Ended";
            default:
                return "";
        }
    }

    public synchronized State getState() { return state; }
    public synchronized String getCallId() { return callId; }

    private boolean matches(String expectedCallId) {
        return expectedCallId != null && expectedCallId.equals(callId);
    }
}
