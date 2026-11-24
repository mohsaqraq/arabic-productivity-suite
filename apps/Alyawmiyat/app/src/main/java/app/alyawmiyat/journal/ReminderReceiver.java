package app.alyawmiyat.journal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!RevisitReminders.ACTION.equals(action)) Reminders.schedule(context,true);
        Context app = context.getApplicationContext();
        String date = intent.getStringExtra(Reminders.DATE);
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                if (RevisitReminders.ACTION.equals(action)) RevisitReminders.deliver(app);
                else if (Reminders.ACTION.equals(action)) Reminders.showDue(app,date);
                else RevisitReminders.schedule(app,true);
            } finally { pending.finish(); }
        },"journal-reminder").start();
    }
}
