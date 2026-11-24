package app.altaqwim.calendar;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;

final class CalendarReminders {
    static final String ACTION="app.altaqwim.calendar.REMIND";
    static final String OPEN="open-item";
    private static final String CHANNEL="calendar-reminders";
    private static final String TAG="calendar-item:";
    private static final int ALARM=81;
    private static final int NOTICE=82;
    private static final int TEST=83;

    private CalendarReminders() {}

    static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager notifications=context.getSystemService(NotificationManager.class);
        NotificationChannel channel=notifications.getNotificationChannel(CHANNEL);
        return notifications.areNotificationsEnabled() &&
            (channel==null || channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }

    static String settings(Context context) throws JSONException {
        return new JSONObject().put("allowed",allowed(context)).put("exact",canExact(context))
            .put("exactSetting",Build.VERSION.SDK_INT>=31).toString();
    }

    private static boolean canExact(Context context) {
        return Build.VERSION.SDK_INT<31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static synchronized void schedule(Context context) {
        AlarmManager alarms=context.getSystemService(AlarmManager.class);
        Intent intent=new Intent(context,ReminderReceiver.class).setAction(ACTION);
        int flags=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_ONE_SHOT;
        PendingIntent old=PendingIntent.getBroadcast(context,ALARM,intent,flags|PendingIntent.FLAG_NO_CREATE);
        if (old!=null) { alarms.cancel(old); old.cancel(); }
        if (!allowed(context)) return;

        ZoneId zone=ZoneId.systemDefault();
        Instant now=Instant.now();
        Instant first=null;
        try (CalendarStore store=new CalendarStore(context)) {
            for (CalendarItem item:CalendarStore.pending(store.getReadableDatabase(),cutoffDay(now,zone))) {
                Instant when=trigger(item,now,zone);
                if (when!=null && (first==null || when.isBefore(first))) first=when;
            }
        }
        if (first==null) return;
        long target=Math.max(first.toEpochMilli(),now.toEpochMilli()+1800);
        PendingIntent alarm=PendingIntent.getBroadcast(context,ALARM,intent,flags);
        if (canExact(context)) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,target,alarm); return; }
            catch (SecurityException ignored) { /* Access can be revoked during scheduling. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,target,alarm);
    }

    private static String cutoffDay(Instant now,ZoneId zone) {
        return now.atZone(zone).toLocalDate().minusDays(1).toString();
    }

    private static Instant trigger(CalendarItem item,Instant now,ZoneId zone) {
        Instant anchor=item.anchor(zone);
        return anchor.isBefore(now.minusSeconds(7200)) ? null : anchor.minusSeconds(item.remind*60L);
    }

    static synchronized void deliver(Context context) {
        if (!allowed(context)) return;
        ZoneId zone=ZoneId.systemDefault();
        Instant now=Instant.now();
        try (CalendarStore store=new CalendarStore(context)) {
            SQLiteDatabase db=store.getWritableDatabase();
            db.beginTransaction();
            try {
                int delivered=0;
                for (CalendarItem item:CalendarStore.pending(db,cutoffDay(now,zone))) {
                    Instant when=trigger(item,now,zone);
                    if (when==null || when.isAfter(now)) continue;
                    if (!post(context,item)) break;
                    CalendarStore.markSent(db,item.id);
                    if (++delivered==24) break;
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        schedule(context);
    }

    static void dismiss(Context context,String id) {
        context.getSystemService(NotificationManager.class).cancel(TAG+id,NOTICE);
    }

    static void dismissAll(Context context) {
        NotificationManager notices=context.getSystemService(NotificationManager.class);
        for (android.service.notification.StatusBarNotification item:notices.getActiveNotifications()) {
            if (item.getTag()!=null && item.getTag().startsWith(TAG)) notices.cancel(item.getTag(),NOTICE);
        }
    }

    static void ensureChannel(Context context) {
        NotificationChannel channel=new NotificationChannel(CHANNEL,"تذكيرات التقويم",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("إشعارات بالمواعيد والمهام التي اخترت تذكيرًا لها.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    static boolean test(Context context) { return post(context,null); }

    private static boolean post(Context context,CalendarItem item) {
        ensureChannel(context);
        if (!allowed(context)) return false;
        boolean test=item==null;
        Intent intent=new Intent(context,MainActivity.class).setData(Uri.parse("altaqwim://item/"+(test ? "test" : item.id)))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!test) intent.putExtra(OPEN,item.id);
        int id=test ? TEST : NOTICE;
        PendingIntent open=PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String title=test ? "تذكير تجريبي من التقويم" : item.kind.equals("event") ? "تذكير بموعد" : "تذكير بمهمة";
        String body=test ? "ستظهر هنا التذكيرات التي تضبطها في التقويم." : item.title+
            (item.start.isEmpty() ? "" : " · \u2066"+item.start+"\u2069");
        Notification notification=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.calendar_notice)
            .setColor(0xff265ebc).setContentTitle(title).setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body)).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_REMINDER).setOnlyAlertOnce(!test).setAutoCancel(true)
            .setContentIntent(open).build();
        try {
            context.getSystemService(NotificationManager.class).notify(test ? null : TAG+item.id,id,notification);
            return true;
        } catch (SecurityException ignored) { return false; }
    }
}
