package app.alyawmiyat.journal;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

final class RevisitReminders {
    static final String ACTION = "app.alyawmiyat.journal.ENTRY_REVISIT";
    static final String OPEN = "open-revisit-entry";
    private static final String CHANNEL = "journal-revisits";
    private static final String TAG = "journal-revisit:";
    private static final int ALARM_ID = 25;
    private static final int NOTICE_ID = 26;
    private static final int BATCH_SIZE = 32;

    private RevisitReminders() {}

    static String dueDate(String day, String interval) {
        if (!EntryData.validDate(day) || interval == null) return "";
        Calendar date = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        setDate(date,day,0);
        switch (interval) {
            case "week": date.add(Calendar.DAY_OF_MONTH,7); break;
            case "month": date.add(Calendar.MONTH,1); break;
            case "year": date.add(Calendar.YEAR,1); break;
            default: return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd",Locale.US);
        format.setTimeZone(date.getTimeZone());
        String due = format.format(date.getTime());
        return EntryData.validDate(due) ? due : "";
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(System.currentTimeMillis());
    }

    static boolean allowed(Context context) {
        return Reminders.allowed(context,CHANNEL);
    }

    static synchronized void schedule(Context context, boolean replace) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        Intent intent = new Intent(context,ReminderReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent existing = PendingIntent.getBroadcast(context,ALARM_ID,intent,flags | PendingIntent.FLAG_NO_CREATE);
        if (existing != null) {
            if (!replace && allowed(context)) return;
            alarms.cancel(existing);
            existing.cancel();
        }
        if (!allowed(context)) return;
        String first;
        try (JournalStore store = new JournalStore(context)) {
            first = store.nextRevisit();
        }
        if (first.isEmpty()) return;
        Calendar due = Calendar.getInstance(Locale.US);
        setDate(due,first,Reminders.time(context));
        long when = Math.max(due.getTimeInMillis(),System.currentTimeMillis() + 3_000);
        PendingIntent pending = PendingIntent.getBroadcast(context,ALARM_ID,intent,flags);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);
    }

    static synchronized void deliver(Context context) {
        if (!allowed(context)) { schedule(context,true); return; }
        Calendar now = Calendar.getInstance(Locale.US);
        int elapsedMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (elapsedMinutes < Reminders.time(context)) now.add(Calendar.DAY_OF_MONTH,-1);
        String cutoff = new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(now.getTime());
        try (JournalStore store = new JournalStore(context)) {
            SQLiteDatabase db = store.getWritableDatabase();
            db.beginTransaction();
            try (Cursor rows = db.rawQuery("SELECT entry_id,period FROM revisits WHERE delivered=0 AND due<=?" +
                " ORDER BY due,entry_id LIMIT " + BATCH_SIZE,new String[]{cutoff})) {
                while (rows.moveToNext()) {
                    String id = rows.getString(0);
                    if (!post(context,id,rows.getString(1))) break;
                    ContentValues sent = new ContentValues();
                    sent.put("delivered",1);
                    db.update("revisits",sent,"entry_id=?",new String[]{id});
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        schedule(context,true);
    }

    private static void setDate(Calendar target,String day,int minute) {
        target.clear();
        target.set(Integer.parseInt(day.substring(0,4)),Integer.parseInt(day.substring(5,7))-1,
            Integer.parseInt(day.substring(8,10)),minute / 60,minute % 60,0);
    }

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,"ذكريات اليوميات",
                NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("تذكير اختياري بالعودة إلى يومية محددة.");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    static void dismiss(Context context,String id) {
        context.getSystemService(NotificationManager.class).cancel(TAG + id,NOTICE_ID);
    }

    static void dismissAll(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getTag() != null && notification.getTag().startsWith(TAG)) {
                manager.cancel(notification.getTag(),NOTICE_ID);
            }
        }
    }

    private static boolean post(Context context, String id, String interval) {
        createChannel(context);
        if (!allowed(context)) return false;
        String text;
        switch (interval) {
            case "week": text = "مرّ أسبوع على هذه اليومية. افتحها واقرأ ما كتبته حينها."; break;
            case "month": text = "مرّ شهر على هذه اليومية. هل ما زلت ترى الأمور بالطريقة نفسها؟"; break;
            default: text = "مرّت سنة على هذه اليومية. خذ لحظة لترى ما تغيّر منذ ذلك اليوم."; break;
        }
        Intent open = new Intent(context,MainActivity.class).setData(Uri.parse("alyawmiyat://revisit/" + id))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(OPEN,id);
        PendingIntent action = PendingIntent.getActivity(context,NOTICE_ID,open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context,CHANNEL) :
            new Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT);
        Notification notice = builder.setSmallIcon(R.drawable.journal_notice).setColor(0xff167e57)
            .setContentTitle("ذكرى من يومياتك").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setOnlyAlertOnce(true)
            .setGroup("journal-revisits").setContentIntent(action).build();
        try {
            context.getSystemService(NotificationManager.class).notify(TAG + id,NOTICE_ID,notice);
            return true;
        } catch (SecurityException ignored) { return false; }
    }
}
