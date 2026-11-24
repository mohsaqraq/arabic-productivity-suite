package app.almahfaza.wallet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class CoachReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!CoachNotifications.ALARM.equals(intent.getAction())) {
            CoachNotifications.schedule(context, true);
            return;
        }
        CoachNotifications.schedule(context, true);
        int scheduledDay = intent.getIntExtra(CoachNotifications.REVIEW_DAY, Integer.MIN_VALUE);
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                CoachNotifications.review(context.getApplicationContext(), scheduledDay);
            } finally {
                pending.finish();
            }
        }, "wallet-review").start();
    }
}
