package app.alyawmiyat.journal;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

final class Reminders {
    static final String ACTION = "app.alyawmiyat.journal.DAILY_REMINDER";
    static final String DATE = "scheduled-date";
    static final String OPEN = "open-from-reminder";
    private static final String CHANNEL = "journal-reminders";
    private static final int ALARM_ID = 22;
    private static final int NOTICE_ID = 23;
    private static final int TEST_ID = 24;
    private static final int DEFAULT_TIME = 20 * 60 + 30;
    private static final String[] MESSAGES = {
        "لو اخترت لحظة واحدة من اليوم لتتذكّرها، شو بتكون؟",
        "كيف مرّ يومك؟ يكفي سطر واحد.",
        "صورة، جملة، أو خطّ سريع. دوّن اللي بتحب تتذكّره.",
        "شو الشيء الصغير اللي كان إله أثر اليوم؟",
        "هدّي الإيقاع لحظة وخذ دقيقة تكتب فيها لنفسك.",
        "أي تفصيل من اليوم بتحب يضل بذاكرتك؟",
        "شو أكثر فكرة ظلّت معك اليوم؟",
        "قبل ما ينتهي اليوم، اترك منه ذكرى صغيرة."
    };

    private Reminders() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("reminders",Context.MODE_PRIVATE);
    }

    static boolean allowed(Context context) { return allowed(context,CHANNEL); }

    static boolean allowed(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel(channelId);
            return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean("enabled",false);
    }

    static void configure(Context context, boolean enabled, int minutes) {
        if (minutes < 0 || minutes >= 24 * 60) return;
        prefs(context).edit().putBoolean("enabled",enabled).putInt("time",minutes).apply();
        if (enabled) createChannel(context);
        else context.getSystemService(NotificationManager.class).cancel(NOTICE_ID);
        schedule(context,true);
    }

    static int time(Context context) { return prefs(context).getInt("time",DEFAULT_TIME); }

    static String settings(Context context) {
        int minutes = time(context);
        try {
            return new JSONObject().put("enabled",enabled(context)).put("allowed",allowed(context)).put("time",
                String.format(Locale.US,"%02d:%02d", minutes / 60, minutes % 60)).toString();
        } catch (JSONException e) { return "{}"; }
    }

    static void schedule(Context context, boolean replace) {
        AlarmManager alarm = context.getSystemService(AlarmManager.class);
        Intent intent = new Intent(context,ReminderReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent existing = PendingIntent.getBroadcast(context,ALARM_ID,intent,flags | PendingIntent.FLAG_NO_CREATE);
        boolean ready = enabled(context) && allowed(context);
        if (existing != null && (!ready || replace)) {
            alarm.cancel(existing);
            existing.cancel();
        } else if (existing != null) return;
        if (!ready) return;
        Calendar now = Calendar.getInstance(Locale.US);
        int minutes = time(context);
        Calendar due = (Calendar) now.clone();
        setTime(due,minutes);
        if (!due.after(now)) {
            due = (Calendar) now.clone();
            due.add(Calendar.DAY_OF_YEAR,1);
            setTime(due,minutes);
        }
        intent.putExtra(DATE,date(due));
        PendingIntent pending = PendingIntent.getBroadcast(context,ALARM_ID,intent,flags);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due.getTimeInMillis(),pending);
    }

    private static void setTime(Calendar date, int minutes) {
        date.set(Calendar.HOUR_OF_DAY,minutes / 60);
        date.set(Calendar.MINUTE,minutes % 60);
        date.set(Calendar.SECOND,0);
        date.set(Calendar.MILLISECOND,0);
    }

    static synchronized void showDue(Context context, String scheduled) {
        Calendar now = Calendar.getInstance(Locale.US);
        String today = date(now);
        if (!today.equals(scheduled) || !enabled(context) || !allowed(context) ||
            today.equals(prefs(context).getString("last",""))) return;
        try (JournalStore store = new JournalStore(context)) {
            if (store.hasEntryOn(today)) return;
        } catch (RuntimeException ignored) { return; }
        String message = MESSAGES[now.get(Calendar.DAY_OF_YEAR) % MESSAGES.length];
        if (post(context,NOTICE_ID,"وقتك مع اليوميات",message)) {
            prefs(context).edit().putString("last",today).apply();
        }
    }

    static boolean sendTest(Context context) {
        return enabled(context) && post(context,TEST_ID,"تذكير تجريبي من اليوميات",MESSAGES[2]);
    }

    private static boolean post(Context context,int id,String title,String content) {
        createChannel(context);
        if (!allowed(context)) return false;
        Intent open = new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(OPEN,true);
        PendingIntent action = PendingIntent.getActivity(context,id,open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context,CHANNEL) :
            new Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT);
        Notification notice = builder.setSmallIcon(R.drawable.journal_notice).setColor(0xff167e57)
            .setContentTitle(title).setContentText(content).setStyle(new Notification.BigTextStyle().bigText(content))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(action).build();
        try {
            context.getSystemService(NotificationManager.class).notify(id,notice);
            return true;
        } catch (SecurityException denied) { return false; }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,"تذكير بالتدوين",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("تذكير يومي اختياري بكتابة يومياتك.");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private static String date(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(calendar.getTime());
    }
}
