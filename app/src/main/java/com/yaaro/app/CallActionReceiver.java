package com.yaaro.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Handles notification Decline (and can route Accept if needed).
 * Stops ringtone, clears notification, opens MainActivity with action.
 */
public class CallActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        String callId = intent.getStringExtra(YaaroFirebaseMessagingService.EXTRA_CALL_ID);
        if (callId == null) callId = "";

        if (YaaroFirebaseMessagingService.ACTION_DECLINE.equals(action)) {
            YaaroRingtonePlayer.stop();
            YaaroFirebaseMessagingService.cancelCallNotification(context, callId);

            Intent open = new Intent(context, MainActivity.class);
            open.setAction(YaaroFirebaseMessagingService.ACTION_DECLINE);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            open.putExtra(YaaroFirebaseMessagingService.EXTRA_FCM_ACTION, "decline");
            open.putExtra(YaaroFirebaseMessagingService.EXTRA_CALL_ID, callId);
            context.startActivity(open);
        }
    }
}
