package com.yaaro.app;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
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
