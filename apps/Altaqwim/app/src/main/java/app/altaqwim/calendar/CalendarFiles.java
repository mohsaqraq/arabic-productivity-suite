package app.altaqwim.calendar;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class CalendarFiles {
    private static final int MAX_BACKUP=32*1024*1024;
    private static final int MAX_ITEMS=20000;
    private static final int[] ICAL_PRIORITIES={0,9,5,1};
    private static final DateTimeFormatter DATE=DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter UTC=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'",Locale.ROOT)
        .withZone(ZoneOffset.UTC);

    static void backup(CalendarStore store,OutputStream output) throws IOException,JSONException {
        SQLiteDatabase db=store.getReadableDatabase();
        write(output,"{\"format\":\"altaqwim\",\"version\":2,\"items\":[");
        try (Cursor rows=db.rawQuery("SELECT "+CalendarStore.FIELDS+",sent FROM items"+CalendarStore.ORDER,null)) {
            int count=0,bytes=70;
            while (rows.moveToNext()) {
                String json=(count==0 ? "" : ",")+CalendarStore.item(rows).json().put("sent",rows.getInt(10)==1);
                byte[] encoded=json.getBytes(StandardCharsets.UTF_8);
                bytes+=encoded.length;
                if (++count>MAX_ITEMS || bytes>MAX_BACKUP) throw new IOException("Backup size");
                output.write(encoded);
            }
        }
        write(output,"]}");
    }

    static int restore(CalendarStore store,InputStream input) throws IOException,JSONException {
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];
        int count;
        while ((count=input.read(buffer))!=-1) {
            if (data.size()+count>MAX_BACKUP) throw new IOException("Backup size");
            data.write(buffer,0,count);
        }
        org.json.JSONTokener parser=new org.json.JSONTokener(data.toString("UTF-8"));
        Object parsed=parser.nextValue();
        if (!(parsed instanceof JSONObject) || parser.nextClean()!=0) throw new JSONException("Invalid backup");
        JSONObject archive=(JSONObject) parsed;
        int version=archive.getInt("version");
        if (!archive.getString("format").equals("altaqwim") || (version!=1 && version!=2)) {
            throw new JSONException("Wrong backup");
        }
        JSONArray items=archive.getJSONArray("items");
        if (items.length()>MAX_ITEMS) throw new JSONException("Too many items");
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("items",null,null);
            for (int i=0;i<items.length();i++) {
                JSONObject source=items.getJSONObject(i);
                ContentValues row=CalendarStore.values(CalendarItem.parse(source));
                row.put("sent",source.optBoolean("sent",false) ? 1 : 0);
                db.insertOrThrow("items",null,row);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return items.length();
    }

    static void ical(CalendarStore store,OutputStream out,ZoneId zone) throws IOException {
        String stamp=UTC.format(Instant.now());
        for (String header:new String[]{"BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Altaqwim//Calendar//AR","CALSCALE:GREGORIAN"}) {
            line(out,header);
        }
        try (Cursor rows=store.getReadableDatabase().rawQuery("SELECT "+CalendarStore.FIELDS+
            " FROM items"+CalendarStore.ORDER,null)) {
            while (rows.moveToNext()) {
                CalendarItem item=CalendarStore.item(rows);
                LocalDate day=LocalDate.parse(item.day);
                boolean event=item.kind.equals("event");
                line(out,event ? "BEGIN:VEVENT" : "BEGIN:VTODO");
                line(out,"UID:"+item.id+"@altaqwim");
                line(out,"DTSTAMP:"+stamp);
                line(out,"SUMMARY:"+escape(item.title));
                line(out,"PRIORITY:"+ICAL_PRIORITIES[item.priority]);
                if (!item.note.isEmpty()) line(out,"DESCRIPTION:"+escape(item.note));
                if (event && item.start.isEmpty()) {
                    line(out,"DTSTART;VALUE=DATE:"+DATE.format(day));
                    line(out,"DTEND;VALUE=DATE:"+DATE.format(day.plusDays(1)));
                } else if (event) {
                    Instant start=item.anchor(zone);
                    Instant end=day.atTime(LocalTime.parse(item.end)).atZone(zone).toInstant();
                    if (!end.isAfter(start)) end=start.plus(Duration.between(LocalTime.parse(item.start),LocalTime.parse(item.end)));
                    line(out,"DTSTART:"+UTC.format(start));
                    line(out,"DTEND:"+UTC.format(end));
                } else {
                    if (item.start.isEmpty()) line(out,"DUE;VALUE=DATE:"+DATE.format(day));
                    else line(out,"DUE:"+UTC.format(item.anchor(zone)));
                    line(out,item.done ? "STATUS:COMPLETED" : "STATUS:NEEDS-ACTION");
                }
                line(out,event ? "END:VEVENT" : "END:VTODO");
            }
        }
        line(out,"END:VCALENDAR");
    }

    static String escape(String text) {
        return text.replace("\\","\\\\").replace("\r\n","\n").replace("\r","\n")
            .replace("\n","\\n").replace(",","\\,").replace(";","\\;");
    }

    static void line(OutputStream output,String text) throws IOException {
        int octets=0;
        for (int index=0;index<text.length();) {
            int code=text.codePointAt(index);
            byte[] part=new String(Character.toChars(code)).getBytes(StandardCharsets.UTF_8);
            if (octets+part.length>75) { write(output,"\r\n "); octets=1; }
            output.write(part);
            octets+=part.length;
            index+=Character.charCount(code);
        }
        write(output,"\r\n");
    }

    private static void write(OutputStream output,String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
