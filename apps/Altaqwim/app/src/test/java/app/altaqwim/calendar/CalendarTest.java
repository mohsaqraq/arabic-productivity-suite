package app.altaqwim.calendar;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class CalendarTest {
    @Test public void monthShowsBothKindsAndWeekKeepsAllOverlappingRows() throws Exception {
        try (CalendarStore store=new CalendarStore(RuntimeEnvironment.getApplication())) {
            String day="2026-11-23";
            store.save(item(1,"event","مَوعِد في المشفى",day,"09:00","10:00","مبنى رئيسي",false,-1));
            store.save(item(2,"event","لقاء في المستشفى",day,"09:30","10:30","",false,-1));
            store.save(item(3,"task","مراجعة محاضرة",day,"","","",false,0));
            assertEquals("[[\"2026-11-23\",2,1,0]]",store.grid(day,day));
            assertEquals(3,new JSONArray(store.list("2026-11-21","2026-11-27",0)).length());
            assertEquals("مَوعِد في المشفى",store.conflict("",day,"09:20","09:25"));
            assertEquals("",store.conflict("",day,"10:30","11:00"));
            assertEquals(1,new JSONArray(store.search("موعد في المشفي",0)).length());
            assertTrue(store.status(id(3),true));
            assertFalse(store.status(id(1),true));
            assertEquals("[[\"2026-11-23\",2,0,1]]",store.grid(day,day));
        }
    }

    @Test public void backupRoundTripsAndInvalidRecordRollsBackAllChanges() throws Exception {
        try (CalendarStore store=new CalendarStore(RuntimeEnvironment.getApplication())) {
            CalendarItem source=item(1,"task","مراجعة", "2026-11-23","12:00","","😀  مراجعة",true,30);
            store.save(source);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            CalendarFiles.backup(store,bytes);
            store.delete(source.id);
            assertEquals(1,CalendarFiles.restore(store,new ByteArrayInputStream(bytes.toByteArray())));
            assertTrue(store.find(source.id).done);
            assertEquals(source.note,store.find(source.id).note);
            JSONObject changed=source.json().put("title","اسم آخر");
            JSONObject invalid=source.json().put("day","2026-02-30");
            String bad=new JSONObject().put("format","altaqwim").put("version",1).put("items",
                new JSONArray().put(changed).put(invalid)).toString();
            assertThrows(JSONException.class,()->CalendarFiles.restore(store,stream(bad)));
            assertEquals(source.title,store.find(source.id).title);
        }
    }

    @Test public void icsExportsLocalTimesAndPreservesArabicWithoutSplittingUtf8() throws Exception {
        try (CalendarStore store=new CalendarStore(RuntimeEnvironment.getApplication())) {
            String arabic="ملاحظات عربية بترميز واضح".repeat(9)+"🙂";
            store.save(item(1,"event","موعد, عادي","2026-11-23","13:30","14:30",arabic,false,-1));
            store.save(item(2,"event","طوال اليوم","2026-11-24","","","",false,-1));
            store.save(item(3,"task","تحضير","2026-11-23","","","",true,-1));
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            CalendarFiles.ical(store,output,ZoneOffset.ofHours(3));
            String text=output.toString("UTF-8");
            for (String line:text.split("\r\n")) assertTrue(line.getBytes(StandardCharsets.UTF_8).length<=75);
            String full=text.replace("\r\n ","");
            assertTrue(full.contains("DTSTART:20261123T103000Z"));
            assertTrue(full.contains("DTEND;VALUE=DATE:20261125"));
            assertTrue(full.contains("BEGIN:VTODO"));
            assertTrue(full.contains("STATUS:COMPLETED"));
            assertTrue(full.contains("DESCRIPTION:"+arabic));
            assertTrue(full.contains("SUMMARY:موعد\\, عادي"));
        }
    }

    @Test public void sendsOnceAndSkipsCompletedTasks() throws Exception {
        Application context=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        LocalDateTime local=LocalDateTime.now().plusMinutes(6);
        String day=local.toLocalDate().toString();
        String time=local.format(DateTimeFormatter.ofPattern("HH:mm",java.util.Locale.ROOT));
        try (CalendarStore store=new CalendarStore(context)) {
            store.save(item(1,"task","مهمة بتنبيه",day,time,"","",false,10));
            store.save(item(2,"task","مهمة منجزة",day,time,"","",true,10));
            CalendarReminders.schedule(context);
            AlarmManager alarms=context.getSystemService(AlarmManager.class);
            assertNotNull(Shadows.shadowOf(alarms).getNextScheduledAlarm());
            CalendarReminders.deliver(context);
            store.save(item(1,"task","تعديل الاسم فقط",day,time,"","",false,10));
            CalendarReminders.deliver(context);
            NotificationManager manager=context.getSystemService(NotificationManager.class);
            List<Notification> notices=Shadows.shadowOf(manager).getAllNotifications();
            assertEquals(1,notices.size());
            assertEquals(id(1),Shadows.shadowOf(notices.get(0).contentIntent).getSavedIntent().getStringExtra(CalendarReminders.OPEN));
            assertEquals(0,CalendarStore.pending(store.getReadableDatabase(),day).size());
        }
    }

    @Test public void migratesExistingItemsAndRestoresLegacyBackup() throws Exception {
        Application context=RuntimeEnvironment.getApplication();
        try (android.database.sqlite.SQLiteDatabase old=context.openOrCreateDatabase("calendar.db",0,null)) {
            old.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, kind TEXT, title TEXT, day TEXT,"+
                "start TEXT,end TEXT,note TEXT,done INTEGER,remind INTEGER,sent INTEGER,searchable TEXT)");
            old.execSQL("INSERT INTO items VALUES (?, 'task','يومية قديمة','2026-11-23','','','حفظ',1,30,1,'قديم')",
                new Object[]{id(1)});
            old.setVersion(1);
        }
        try (CalendarStore store=new CalendarStore(context)) {
            CalendarItem migrated=store.find(id(1));
            assertTrue(migrated.done);
            assertEquals(0,migrated.priority);
            assertEquals(30,migrated.remind);
            assertEquals("حفظ",migrated.note);
            JSONObject legacy=migrated.json();
            legacy.remove("priority");
            String backup=new JSONObject().put("format","altaqwim").put("version",1)
                .put("items",new JSONArray().put(legacy)).toString();
            assertEquals(1,CalendarFiles.restore(store,stream(backup)));
            assertEquals(0,store.find(id(1)).priority);
        }
    }

    @Test public void prioritySurvivesEditingAndFilesAndTheBoardLimitsEachDay() throws Exception {
        try (CalendarStore store=new CalendarStore(RuntimeEnvironment.getApplication())) {
            for(int i=1;i<=4;i++){
                String day=i<4?"2026-11-23":"2026-11-24";
                CalendarItem created=item(i,"event","عنصر "+i,day,"14:00","15:00","",false,-1);
                store.save(CalendarItem.parse(created.json().put("priority",i-1)));
            }
            CalendarItem high=store.find(id(4));
            assertEquals(3,high.priority);
            assertThrows(JSONException.class,()->CalendarItem.parse(high.json().put("priority",5)));
            JSONArray board=new JSONArray(store.board("2026-11-23","2026-11-24",2));
            assertEquals(3,board.length());
            assertEquals("2026-11-24",board.getJSONObject(2).getString("day"));
            assertEquals(3,board.getJSONObject(2).getInt("priority"));
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            CalendarFiles.backup(store,out);
            store.delete(id(4));
            assertEquals(4,CalendarFiles.restore(store,new ByteArrayInputStream(out.toByteArray())));
            assertEquals(3,store.find(id(4)).priority);
            ByteArrayOutputStream ics=new ByteArrayOutputStream();
            CalendarFiles.ical(store,ics,ZoneOffset.UTC);
            String doc=ics.toString("UTF-8");
            for(int p:new int[]{0,1,5,9}) assertTrue(doc.contains("PRIORITY:"+p+"\r\n"));
        }
    }

    private static CalendarItem item(int id,String kind,String title,String day,String start,String end,String note,boolean done,int lead)
        throws JSONException {
        return CalendarItem.parse(new CalendarItem(id(id),kind,title,day,start,end,note,done,lead,0).json());
    }

    private static String id(int digit) { return "0000000"+digit+"-0000-0000-0000-000000000000"; }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
