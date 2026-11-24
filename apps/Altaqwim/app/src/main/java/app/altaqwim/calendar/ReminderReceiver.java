package app.altaqwim.calendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        Context app=context.getApplicationContext();
        String action=intent.getAction();
        PendingResult result=goAsync();
        new Thread(() -> {
            try {
                if (CalendarReminders.ACTION.equals(action)) CalendarReminders.deliver(app);
                else CalendarReminders.schedule(app);
            } finally { result.finish(); }
        },"calendar-reminder").start();
    }
}
