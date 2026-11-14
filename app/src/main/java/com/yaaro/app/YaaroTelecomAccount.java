package com.yaaro.app;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/** Registers the self-managed Telecom account; does not answer or start audio. */
public final class YaaroTelecomAccount {
    private static final String TAG = "YaaroTelecom";
    private static final String ACCOUNT_ID = "qyaaro_voice";
    private YaaroTelecomAccount() {}

    public static PhoneAccountHandle handle(Context context) {
        return new PhoneAccountHandle(
                new ComponentName(context, YaaroConnectionService.class), ACCOUNT_ID);
    }

    /**
     * Request Telecom ownership only after Firebase has verified a ringing
     * voice call belonging to the current receiver. This method never starts
     * microphone capture or opens an Activity.
     */
    public static boolean reportVerifiedIncomingCall(Context context, String callId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                callId == null || callId.trim().isEmpty()) return false;
        TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecom == null) return false;
        try {
            PhoneAccountHandle accountHandle = handle(context);
            PhoneAccount account = telecom.getPhoneAccount(accountHandle);
            if (account == null ||
                    (account.getCapabilities() & PhoneAccount.CAPABILITY_SELF_MANAGED) == 0) {
                Log.w(TAG, "Self-managed account unavailable for incoming call");
                return false;
            }
            Bundle extras = new Bundle();
            Bundle incomingExtras = new Bundle();
            incomingExtras.putString("com.yaaro.app.CALL_ID", callId);
            extras.putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, incomingExtras);
            telecom.addNewIncomingCall(accountHandle, extras);
            return true;
        } catch (SecurityException | IllegalArgumentException | IllegalStateException error) {
            Log.w(TAG, "Telecom incoming call request denied", error);
            return false;
        }
    }

    public static boolean register(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecom == null) return false;
        try {
            PhoneAccount account = PhoneAccount.builder(handle(context), "QYAARO Voice")
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .build();
            telecom.registerPhoneAccount(account);
            return true;
        } catch (SecurityException | IllegalArgumentException error) {
            Log.w(TAG, "Self-managed phone account registration denied", error);
            return false;
        }
    }
}
