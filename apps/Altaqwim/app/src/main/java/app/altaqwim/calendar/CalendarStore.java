package app.altaqwim.calendar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CalendarStore extends SQLiteOpenHelper {
    static final String FIELDS="id,kind,title,day,start,end,note,done,remind,priority";
    private static final String SUMMARY_FIELDS="id,kind,title,day,start,end,substr(note,1,140),done,remind,priority";
    static final String ORDER=" ORDER BY day,start,kind,title,id";

    CalendarStore(Context context) {
        super(context.getApplicationContext(),"calendar.db",null,2);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items(id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL,title TEXT NOT NULL,"+
            "day TEXT NOT NULL,start TEXT NOT NULL,end TEXT NOT NULL,note TEXT NOT NULL,done INTEGER NOT NULL,"+
            "remind INTEGER NOT NULL,sent INTEGER NOT NULL DEFAULT 0,searchable TEXT NOT NULL,"+
            "priority INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX item_date ON items(day,start)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int from,int to) {
        if (from==1 && to==2) db.execSQL("ALTER TABLE items ADD COLUMN priority INTEGER NOT NULL DEFAULT 0");
        else throw new IllegalStateException("Unsupported calendar schema");
    }

    static CalendarItem item(Cursor row) {
        return new CalendarItem(row.getString(0),row.getString(1),row.getString(2),row.getString(3),
            row.getString(4),row.getString(5),row.getString(6),row.getInt(7)==1,row.getInt(8),row.getInt(9));
    }

    CalendarItem find(String id) {
        if (!CalendarItem.validId(id)) return null;
        try (Cursor row=getReadableDatabase().rawQuery("SELECT "+FIELDS+" FROM items WHERE id=?",new String[]{id})) {
            return row.moveToFirst() ? item(row) : null;
        }
    }

    String grid(String first,String last) throws JSONException {
        if (!CalendarItem.validDay(first) || !CalendarItem.validDay(last)) return "[]";
        JSONArray result=new JSONArray();
        String query="SELECT day, SUM(CASE WHEN kind='event' THEN 1 ELSE 0 END), "+
            "SUM(CASE WHEN kind='task' AND done=0 THEN 1 ELSE 0 END), "+
            "SUM(CASE WHEN done=1 THEN 1 ELSE 0 END) FROM items WHERE day BETWEEN ? AND ? GROUP BY day";
        try (Cursor row=getReadableDatabase().rawQuery(query,new String[]{first,last})) {
            while (row.moveToNext()) result.put(new JSONArray().put(row.getString(0)).put(row.getInt(1))
                .put(row.getInt(2)).put(row.getInt(3)));
        }
        return result.toString();
    }

    String board(String first,String last,int limit) throws JSONException {
        if (!CalendarItem.validDay(first) || !CalendarItem.validDay(last) ||
            java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(first),java.time.LocalDate.parse(last))>42 ||
            limit<1 || limit>80) return "[]";
        JSONArray result=new JSONArray();
        String day="";
        int count=0;
        try (Cursor rows=getReadableDatabase().rawQuery("SELECT "+SUMMARY_FIELDS+
            " FROM items WHERE day BETWEEN ? AND ?"+ORDER,new String[]{first,last})) {
            while (rows.moveToNext()) {
                String current=rows.getString(3);
                if (!day.equals(current)) { day=current; count=0; }
                if (count++<limit) result.put(item(rows).json());
            }
        }
        return result.toString();
    }

    String list(String first,String last,int offset) throws JSONException {
        if (!CalendarItem.validDay(first) || !CalendarItem.validDay(last)) return "[]";
        return summaries("day BETWEEN ? AND ?",new String[]{first,last},offset);
    }

    String conflict(String id,String day,String start,String end) {
        if (!CalendarItem.validDay(day) || start==null || end==null || start.length()!=5 || end.length()!=5) return "";
        try (Cursor row=getReadableDatabase().rawQuery(
            "SELECT title FROM items WHERE id<>? AND day=? AND kind='event' AND start<>'' AND start<? AND end>?" +
                " ORDER BY start,id LIMIT 1",new String[]{id,day,end,start})) {
            return row.moveToFirst() ? row.getString(0) : "";
        }
    }

    String search(String text,int offset) throws JSONException {
        String term=normalize(text==null ? "" : text);
        if (term.isEmpty() || term.length()>140) return "[]";
        return summaries("searchable LIKE ? ESCAPE '~'",
            new String[]{"%"+term.replace("~","~~").replace("%","~%").replace("_","~_")+"%"},offset);
    }

    private String summaries(String where,String[] args,int offset) throws JSONException {
        JSONArray result=new JSONArray();
        try (Cursor row=getReadableDatabase().rawQuery("SELECT "+SUMMARY_FIELDS+" FROM items WHERE "+where+
            ORDER+" LIMIT 41 OFFSET "+Math.max(0,offset),args)) {
            while (row.moveToNext()) result.put(item(row).json());
        }
        return result.toString();
    }

    void save(CalendarItem value) {
        SQLiteDatabase db=getWritableDatabase();
        ContentValues row=values(value);
        db.beginTransaction();
        try {
            try (Cursor old=db.rawQuery("SELECT day,start,remind,sent FROM items WHERE id=?",new String[]{value.id})) {
                if (old.moveToFirst() && value.day.equals(old.getString(0)) && value.start.equals(old.getString(1)) &&
                    value.remind==old.getInt(2)) row.put("sent",old.getInt(3));
            }
            db.insertWithOnConflict("items",null,row,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    static ContentValues values(CalendarItem value) {
        ContentValues row=new ContentValues();
        row.put("id",value.id); row.put("kind",value.kind); row.put("title",value.title);
        row.put("day",value.day); row.put("start",value.start); row.put("end",value.end);
        row.put("note",value.note); row.put("done",value.done ? 1 : 0); row.put("remind",value.remind); row.put("priority",value.priority);
        row.put("searchable",normalize(value.title+"\n"+value.note));
        return row;
    }

    boolean delete(String id) {
        return CalendarItem.validId(id) && getWritableDatabase().delete("items","id=?",new String[]{id})==1;
    }

    boolean status(String id,boolean done) {
        if (!CalendarItem.validId(id)) return false;
        ContentValues row=new ContentValues(); row.put("done",done ? 1 : 0);
        return getWritableDatabase().update("items",row,"id=? AND kind='task'",new String[]{id})==1;
    }

    static List<CalendarItem> pending(SQLiteDatabase db,String first) {
        List<CalendarItem> entries=new ArrayList<>();
        try (Cursor row=db.rawQuery("SELECT "+FIELDS+
            " FROM items WHERE done=0 AND remind>=0 AND sent=0 AND day>=? ORDER BY day,start",new String[]{first})) {
            while (row.moveToNext()) entries.add(item(row));
        }
        return entries;
    }

    static void markSent(SQLiteDatabase db,String id) {
        ContentValues value=new ContentValues(); value.put("sent",1);
        db.update("items",value,"id=?",new String[]{id});
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("[\\p{M}ـ]","")
            .replace('ى','ي').toLowerCase(Locale.ROOT).trim();
    }
}
