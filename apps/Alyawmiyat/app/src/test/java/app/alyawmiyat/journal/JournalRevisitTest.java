package app.alyawmiyat.journal;

import android.Manifest;
import android.app.Application;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class JournalRevisitTest {
    private static final String ID = "00000001-0000-0000-0000-000000000000";

    @Test public void periodsUseCalendarDates() {
        assertEquals("2026-01-07",RevisitReminders.dueDate("2025-12-31","week"));
        assertEquals("2026-02-28",RevisitReminders.dueDate("2026-01-31","month"));
        assertEquals("2028-02-29",RevisitReminders.dueDate("2028-01-31","month"));
        assertEquals("2029-02-28",RevisitReminders.dueDate("2028-02-29","year"));
        assertEquals("",RevisitReminders.dueDate("2030-02-30","month"));
        assertEquals("",RevisitReminders.dueDate("9999-12-31","year"));
    }

    @Test public void migratingAnExistingDatabasePreservesTheJournal() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String day = RevisitReminders.today();
        try (JournalStore original = new JournalStore(context)) {
            original.save(entry(ID,day,"محفوظ قبل التحديث","[[false,[[12,32,50]]]]"));
            SQLiteDatabase db = original.getWritableDatabase();
            db.execSQL("DROP TABLE revisits");
            db.setVersion(1);
        }
        try (JournalStore updated = new JournalStore(context)) {
            JSONObject saved = new JSONObject(updated.read(ID));
            assertEquals("محفوظ قبل التحديث",saved.getString("title"));
            assertEquals(2,saved.getJSONArray("ink").getJSONArray(0).length());
            updated.setRevisit(ID,"year");
            assertEquals(RevisitReminders.dueDate(day,"year"),updated.nextRevisit());
        }
    }

    @Test public void reminderFollowsAnEditedDateAndCascadesOnDelete() throws Exception {
        try (JournalStore store = new JournalStore(RuntimeEnvironment.getApplication())) {
            String today = RevisitReminders.today();
            store.save(entry(ID,today,"يومية","[]"));
            String first = RevisitReminders.dueDate(today,"month");
            assertEquals(first,new JSONObject(store.setRevisit(ID,"month")).getString("due"));
            assertFalse(store.save(entry(ID,today,"تغيير العنوان فقط","[]")));
            String tomorrow = RevisitReminders.dueDate(today,"week");
            assertTrue(store.save(entry(ID,tomorrow,"تغيير التاريخ","[]")));
            assertEquals(RevisitReminders.dueDate(tomorrow,"month"),store.nextRevisit());
            assertEquals("null",store.setRevisit(ID,""));
            assertEquals("",store.nextRevisit());
            store.setRevisit(ID,"year");
            assertTrue(store.deleteEntry(ID));
            assertEquals("",store.nextRevisit());
        }
    }

    @Test public void v1ArchiveAndNewStrokeWidthsRoundTrip() throws Exception {
        try (JournalStore store = new JournalStore(RuntimeEnvironment.getApplication())) {
            String day = RevisitReminders.today();
            ByteArrayOutputStream v1 = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(v1)) {
                add(zip,"journal.json","{\"format\":\"alyawmiyat\",\"version\":1}");
                add(zip,"entries/" + ID + ".json",entry(ID,day,"قديمة","[[false,[[1,2,50]]]]").json().toString());
                add(zip,"complete.json","{\"entries\":1,\"photos\":0}");
            }
            assertEquals(1,ArchiveIO.importBackup(store,new ByteArrayInputStream(v1.toByteArray())));
            assertEquals(2,new JSONObject(store.read(ID)).getJSONArray("ink").getJSONArray(0).length());
            String mixed = "[[false,[[1,2,50]]],[false,[[8,9,70]],2],[false,[[5,6,80]],0]]";
            store.save(entry(ID,day,"جديدة",mixed));
            store.setRevisit(ID,"year");
            ByteArrayOutputStream v2 = new ByteArrayOutputStream();
            ArchiveIO.exportBackup(store,v2);
            store.deleteEntry(ID);
            assertEquals(1,ArchiveIO.importBackup(store,new ByteArrayInputStream(v2.toByteArray())));
            JSONObject restored = new JSONObject(store.read(ID));
            assertEquals(mixed,restored.getJSONArray("ink").toString());
            assertEquals(RevisitReminders.dueDate(day,"year"),restored.getJSONObject("revisit").getString("due"));
            assertThrows(JSONException.class,() -> EntryData.parse(
                entry(ID,day,"","[[false,[[8,9,70]],3]]").json().toString()));
        }
    }

    @Test public void oneNotificationPerEntryEvenWithDailyReminderOff() throws Exception {
        Application context = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        try (JournalStore store = new JournalStore(context)) {
            EntryData old = entry(ID,"2024-01-01","عنوان خاص","[]");
            store.save(old);
            String due = RevisitReminders.dueDate(old.day,"year");
            JournalStore.writeRevisit(store.getWritableDatabase(),ID,"year",due,false);
            assertFalse(Reminders.enabled(context));
            RevisitReminders.schedule(context,true);
            assertNotNull(Shadows.shadowOf(alarms).getNextScheduledAlarm());
            RevisitReminders.deliver(context);
            RevisitReminders.deliver(context);
            List<Notification> notices = Shadows.shadowOf(manager).getAllNotifications();
            assertEquals(1,notices.size());
            Notification notice = notices.get(0);
            assertEquals(ID,Shadows.shadowOf(notice.contentIntent).getSavedIntent().getStringExtra(RevisitReminders.OPEN));
            assertFalse(notice.extras.getString(Notification.EXTRA_TITLE).contains("عنوان خاص"));
            assertTrue(new JSONObject(store.revisitData(ID)).getBoolean("delivered"));
            assertEquals("",store.nextRevisit());
        }
    }

    private static EntryData entry(String id,String day,String title,String ink) throws JSONException {
        return new EntryData(id,day,title,"","ink",new JSONArray(ink),24);
    }

    private static void add(ZipOutputStream output,String name,String content) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
