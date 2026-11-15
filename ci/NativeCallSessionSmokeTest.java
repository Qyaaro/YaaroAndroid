package com.yaaro.app;

/** Standalone JVM smoke test; run with javac/java, no Android runtime needed. */
public final class NativeCallSessionSmokeTest {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        NativeCallSession call = new NativeCallSession();
        require("".equals(call.getDisplayStatus()), "idle must not display a status");
        require(!call.beginAnswer("call-a"), "cannot answer before ringing");
        require(call.incoming("call-a"), "valid incoming call");
        require("Ringing".equals(call.getDisplayStatus()), "ringing label");
        require(!call.incoming("call-b"), "second call must not overwrite active call");
        require(!call.beginAnswer("call-b"), "wrong call cannot answer");
        require(call.beginAnswer("call-a"), "answer starts connecting");
        require("Ringing".equals(call.getDisplayStatus()), "connecting must be hidden");
        require(!call.connected("call-b"), "wrong call cannot connect");
        require(call.connected("call-a"), "matching call connects");
        require("Connected".equals(call.getDisplayStatus()), "connected label");
        require(call.end("call-a"), "matching call ends");
        require("Ended".equals(call.getDisplayStatus()), "ended label");
        require(!call.end("call-a"), "end must be idempotent");
        require(call.incoming("call-c"), "new call allowed after ended");
        System.out.println("NativeCallSession smoke tests passed");
    }
}
