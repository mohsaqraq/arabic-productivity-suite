package app.almahfaza.wallet;

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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

final class CoachNotifications {
    static final String ALARM = "app.almahfaza.wallet.REVIEW_SPENDING";
    static final String REVIEW_DAY = "review-day";
    static final String OPEN_REVIEW = "open-spending-review";

    static final int DEFAULT_TIME = 9 * 60;
    static final int REAL = 71;
    static final int EXAMPLE = 72;
    private static final int ALARM_REQUEST = 73;
    private static final int GLOBAL_GAP = 2;
    private static final int TOPIC_GAP = 7;
    private static final int CATEGORY_GAP = 5;
    private static final String CHANNEL = "spending-insights";
    private static final String ENABLED = "coach-enabled";
    private static final String TIME = "coach-time";
    private static final String LAST_DAY = "coach-last-day";
    private static final String LAST_NOTICE = "coach-last-notice";
    private static final String PERMISSION_ASKED = "coach-permission-asked";

    private CoachNotifications() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("wallet", Context.MODE_PRIVATE);
    }

    private static int time(SharedPreferences prefs) {
        return prefs.getInt(TIME, DEFAULT_TIME);
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(ENABLED, false);
    }

    static boolean wasAsked(Context context) {
        return prefs(context).getBoolean(PERMISSION_ASKED, false);
    }

    static void markAsked(Context context) {
        prefs(context).edit().putBoolean(PERMISSION_ASKED, true).apply();
    }

    static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
    }

    static void configure(Context context, boolean enabled, int minutes) {
        if (minutes < 0 || minutes >= 24 * 60) return;
        prefs(context).edit().putBoolean(ENABLED, enabled).putInt(TIME, minutes).apply();
        if (enabled) createChannel(context);
        else dismiss(context);
        schedule(context, true);
    }

    static void dismiss(Context context) {
        context.getSystemService(NotificationManager.class).cancel(REAL);
    }

    static void schedule(Context context, boolean recalculate) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        Intent alarm = new Intent(context, CoachReceiver.class).setAction(ALARM);
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent existing = PendingIntent.getBroadcast(context, ALARM_REQUEST, alarm,
            flags | PendingIntent.FLAG_NO_CREATE);
        if (!enabled(context) || !allowed(context)) {
            if (existing != null) {
                alarms.cancel(existing);
                existing.cancel();
            }
            return;
        }
        if (existing != null && !recalculate) return;
        if (existing != null) {
            alarms.cancel(existing);
            existing.cancel();
        }
        Calendar now = Calendar.getInstance();
        Calendar due = (Calendar) now.clone();
        int minutes = time(prefs(context));
        setTime(due, minutes);
        if (!due.after(now)) {
            due = (Calendar) now.clone();
            due.add(Calendar.DAY_OF_YEAR, 1);
            setTime(due, minutes);
        }
        alarm.putExtra(REVIEW_DAY, SpendingInsights.localDay(due));
        PendingIntent pending = PendingIntent.getBroadcast(context, ALARM_REQUEST, alarm, flags);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due.getTimeInMillis(), pending);
    }

    private static void setTime(Calendar day, int minutes) {
        day.set(Calendar.HOUR_OF_DAY, minutes / 60);
        day.set(Calendar.MINUTE, minutes % 60);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
    }

    static String configuration(Context context) {
        try {
            JSONObject json = new JSONObject();
            int time = time(prefs(context));
            json.put("time", String.format(Locale.US, "%02d:%02d", time / 60, time % 60));
            json.put("enabled", enabled(context));
            json.put("allowed", allowed(context));
            return json.toString();
        } catch (JSONException ignored) { return "{}"; }
    }

    static String preview(Context context) {
        int today = SpendingInsights.localDay(Calendar.getInstance());
        List<SpendingInsights.Insight> items = SpendingInsights.evaluate(prefs(context).getString("state", ""), today);
        return items.isEmpty() ? "" : toJson(items.get(0));
    }

    static String lastNotice(Context context) {
        return prefs(context).getString(LAST_NOTICE, "");
    }

    static synchronized void review(Context context, int scheduledDay) {
        int today = SpendingInsights.localDay(Calendar.getInstance());
        if (scheduledDay != today || !enabled(context) || !allowed(context)) return;
        SharedPreferences preferences = prefs(context);
        if (today - preferences.getInt(LAST_DAY, 0) < GLOBAL_GAP) return;
        String state = preferences.getString("state", "");
        List<SpendingInsights.Insight> items = SpendingInsights.evaluate(state, today);
        for (SpendingInsights.Insight item : items) {
            String topic = "coach-kind-" + item.kind;
            String category = "coach-category-" + item.category;
            if (item.oncePerMonth()) {
                if (item.month.equals(preferences.getString(topic, ""))) continue;
            } else if (today - preferences.getInt(topic, 0) < TOPIC_GAP) continue;
            if (!item.category.isEmpty() && today - preferences.getInt(category, 0) < CATEGORY_GAP) continue;

            if (!enabled(context) || !allowed(context)) return;
            String currentState = preferences.getString("state", "");
            if (!state.equals(currentState)) return;
            if (!post(context, REAL, item.title, item.body, hideAmounts(currentState))) return;
            SharedPreferences.Editor editor = preferences.edit().putInt(LAST_DAY, today)
                .putString(LAST_NOTICE, toJson(item));
            if (item.oncePerMonth()) editor.putString(topic, item.month);
            else editor.putInt(topic, today);
            if (!item.category.isEmpty()) editor.putInt(category, today);
            editor.commit();
            return;
        }
    }

    static boolean sendExample(Context context) {
        createChannel(context);
        return allowed(context) && post(context, EXAMPLE, "إشعار تجريبي من المحفظة",
            "هذا مثال. عند ملاحظة تغيّر واضح في مصروفك، ستظهر هنا ملاحظة مبنية على سجلك.", false);
    }

    private static boolean hideAmounts(String state) {
        try {
            return new JSONObject(state).optBoolean("hide", false);
        } catch (JSONException ignored) { return true; }
    }

    private static String toJson(SpendingInsights.Insight item) {
        try {
            JSONObject json = new JSONObject();
            json.put("title", item.title);
            json.put("body", item.body);
            json.put("month", item.month);
            return json.toString();
        } catch (JSONException ignored) { return ""; }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "رؤى الإنفاق",
                NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("ملاحظات حول أنماط الصرف والميزانية الشهرية.");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private static Notification.Builder builder(Context context) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL) :
            new Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT);
    }

    private static boolean post(Context context, int id, String title, String body, boolean privateOnly) {
        createChannel(context);
        if (!allowed(context)) return false;
        Intent intent = new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(OPEN_REVIEW, id);
        PendingIntent open = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String privateTitle = "المحفظة";
        String privateBody = "توجد ملاحظة جديدة عن إنفاقك.";
        Notification publicNotice = builder(context)
            .setSmallIcon(R.drawable.wallet_notice).setContentTitle(privateTitle).setContentText(privateBody)
            .build();
        Notification notification = builder(context)
            .setSmallIcon(R.drawable.wallet_notice)
            .setColor(0xff6e46db)
            .setContentTitle(privateOnly ? privateTitle : title)
            .setContentText(privateOnly ? privateBody : body)
            .setStyle(new Notification.BigTextStyle().bigText(privateOnly ? privateBody : body))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotice)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build();
        try {
            context.getSystemService(NotificationManager.class).notify(id, notification);
            return true;
        } catch (SecurityException ignored) { return false; }
    }
}
