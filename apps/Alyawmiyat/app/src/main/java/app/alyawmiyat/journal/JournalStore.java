package app.alyawmiyat.journal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class JournalStore extends SQLiteOpenHelper {
    static final int PAGE_SIZE = 30;
    static final int MAX_PHOTOS = 6;
    static final int MAX_PHOTO_BYTES = 768 * 1024;

    JournalStore(Context context) {
        super(context.getApplicationContext(), "journal.db", null, 2);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY NOT NULL, day TEXT NOT NULL, title TEXT NOT NULL," +
            " body TEXT NOT NULL, mode TEXT NOT NULL, ink TEXT NOT NULL, updated INTEGER NOT NULL, search TEXT NOT NULL)");
        db.execSQL("CREATE INDEX entry_order ON entries(day DESC, updated DESC, id)");
        db.execSQL("CREATE TABLE photos (id TEXT PRIMARY KEY NOT NULL, entry_id TEXT NOT NULL, position INTEGER NOT NULL," +
            " data BLOB NOT NULL, FOREIGN KEY(entry_id) REFERENCES entries(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX photo_entry ON photos(entry_id, position)");
        createRevisits(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion >= 2) createRevisits(db);
    }

    private static void createRevisits(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE revisits (entry_id TEXT PRIMARY KEY NOT NULL, period TEXT NOT NULL," +
            " due TEXT NOT NULL, delivered INTEGER NOT NULL DEFAULT 0," +
            " FOREIGN KEY(entry_id) REFERENCES entries(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX revisit_due ON revisits(delivered,due)");
    }

    String list(String query, String day, int offset) throws JSONException {
        SQLiteDatabase db = getReadableDatabase();
        List<String> args = new ArrayList<>();
        String where = " WHERE 1=1";
        if (EntryData.validDate(day)) { where += " AND day=?"; args.add(day); }
        String term = normalize(query == null ? "" : query);
        if (!term.isEmpty()) {
            where += " AND search LIKE ? ESCAPE '~'";
            args.add("%" + term.replace("~","~~").replace("%","~%").replace("_","~_") + "%");
        }
        JSONArray entries = new JSONArray();
        boolean more;
        try (Cursor rows = db.rawQuery(
                "SELECT id, day, title, substr(body,1,160), mode, (SELECT COUNT(*) FROM photos WHERE entry_id=entries.id)" +
                " FROM entries" + where + " ORDER BY day DESC,updated DESC,id LIMIT " + (PAGE_SIZE + 1) +
                " OFFSET " + Math.max(0, offset), args.toArray(new String[0]))) {
            while (entries.length() < PAGE_SIZE && rows.moveToNext()) {
                entries.put(new JSONObject().put("id", rows.getString(0)).put("day", rows.getString(1))
                    .put("title", rows.getString(2)).put("excerpt", rows.getString(3))
                    .put("mode", rows.getString(4)).put("photos", rows.getInt(5)));
            }
            more = rows.moveToNext();
        }
        return new JSONObject().put("entries", entries).put("more", more).toString();
    }

    String read(String id) throws JSONException {
        SQLiteDatabase db = getReadableDatabase();
        EntryData entry = load(db, id);
        return entry == null ? "" : entry.json().put("photos", new JSONArray(photoIds(db, id)))
            .put("revisit",revisit(db,id)).toString();
    }

    static EntryData load(SQLiteDatabase db, String id) throws JSONException {
        if (!EntryData.validId(id)) return null;
        try (Cursor row = db.rawQuery("SELECT day,title,body,mode,ink,updated FROM entries WHERE id=?", new String[]{id})) {
            if (!row.moveToFirst()) return null;
            return new EntryData(id,row.getString(0),row.getString(1),row.getString(2),row.getString(3),
                new JSONArray(row.getString(4)),row.getLong(5));
        }
    }

    boolean save(EntryData entry) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            write(db, entry);
            JSONObject reminder = revisit(db,entry.id);
            boolean retimed = false;
            if (reminder != null && !reminder.getBoolean("delivered")) {
                String due = RevisitReminders.dueDate(entry.day,reminder.getString("period"));
                if (!due.isEmpty() && !due.equals(reminder.getString("due"))) {
                    writeRevisit(db,entry.id,reminder.getString("period"),due,false);
                    retimed = true;
                }
            }
            db.setTransactionSuccessful();
            return retimed;
        } finally { db.endTransaction(); }
    }

    static JSONObject revisit(SQLiteDatabase db,String id) throws JSONException {
        try (Cursor row = db.rawQuery("SELECT period,due,delivered FROM revisits WHERE entry_id=?",new String[]{id})) {
            return row.moveToFirst() ? new JSONObject().put("period",row.getString(0)).put("due",row.getString(1))
                .put("delivered",row.getInt(2) == 1) : null;
        }
    }

    String revisitData(String id) throws JSONException {
        if (!EntryData.validId(id)) throw new IllegalArgumentException("Invalid entry");
        JSONObject saved = revisit(getReadableDatabase(),id);
        return saved == null ? "null" : saved.toString();
    }

    String setRevisit(String id,String interval) throws JSONException {
        if (!EntryData.validId(id) || interval == null) throw new IllegalArgumentException("Invalid entry");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (interval.isEmpty()) {
                db.delete("revisits","entry_id=?",new String[]{id});
            } else {
                EntryData entry = load(db,id);
                String due = entry == null ? "" : RevisitReminders.dueDate(entry.day,interval);
                if (due.isEmpty() || due.compareTo(RevisitReminders.today()) < 0) throw new IllegalArgumentException("Past date");
                JSONObject previous = revisit(db,id);
                if (previous == null || !interval.equals(previous.getString("period"))) {
                    writeRevisit(db,id,interval,due,false);
                }
            }
            JSONObject saved = revisit(db,id);
            db.setTransactionSuccessful();
            return saved == null ? "null" : saved.toString();
        } finally { db.endTransaction(); }
    }

    static void writeRevisit(SQLiteDatabase db,String id,String period,String due,boolean delivered) {
        ContentValues row = new ContentValues();
        row.put("entry_id",id);
        row.put("period",period);
        row.put("due",due);
        row.put("delivered",delivered ? 1 : 0);
        db.insertWithOnConflict("revisits",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }

    String nextRevisit() {
        try (Cursor row = getReadableDatabase().rawQuery("SELECT due FROM revisits WHERE delivered=0 ORDER BY due LIMIT 1",null)) {
            return row.moveToFirst() ? row.getString(0) : "";
        }
    }

    static void write(SQLiteDatabase db, EntryData entry) {
        ContentValues values = new ContentValues();
        values.put("day",entry.day);
        values.put("title",entry.title);
        values.put("body",entry.body);
        values.put("mode",entry.mode);
        values.put("ink",entry.ink.toString());
        values.put("updated", entry.updated);
        values.put("search",normalize(entry.title + "\n" + entry.body));
        if (db.update("entries",values,"id=?",new String[]{entry.id}) == 0) {
            values.put("id",entry.id);
            db.insertOrThrow("entries",null,values);
        }
    }

    boolean deleteEntry(String id) {
        return EntryData.validId(id) && getWritableDatabase().delete("entries","id=?",new String[]{id}) > 0;
    }

    boolean deletePhoto(String entry, String id) {
        return EntryData.validId(entry) && EntryData.validId(id) &&
            getWritableDatabase().delete("photos", "entry_id=? AND id=?", new String[]{entry,id}) > 0;
    }

    String photos(String entry) {
        return EntryData.validId(entry) ? new JSONArray(photoIds(getReadableDatabase(),entry)).toString() : "[]";
    }

    static List<String> photoIds(SQLiteDatabase db, String entry) {
        List<String> photos = new ArrayList<>();
        try (Cursor rows = db.rawQuery("SELECT id FROM photos WHERE entry_id=? ORDER BY position",new String[]{entry})) {
            while (rows.moveToNext()) photos.add(rows.getString(0));
        }
        return photos;
    }

    byte[] photo(String id) {
        return photo(getReadableDatabase(), id);
    }

    static byte[] photo(SQLiteDatabase db, String id) {
        if (!EntryData.validId(id)) return null;
        try (Cursor row = db.rawQuery("SELECT data FROM photos WHERE id=?",new String[]{id})) {
            return row.moveToFirst() ? row.getBlob(0) : null;
        }
    }

    boolean addPhoto(EntryData fallback, String photo, byte[] bytes) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            EntryData entry = load(db, fallback.id);
            if (entry == null) {
                write(db, fallback);
                entry = fallback;
            }
            if (!entry.mode.equals("text")) return false;
            if (!insertPhoto(db, entry.id, photo, bytes)) return false;
            ContentValues updated = new ContentValues();
            updated.put("updated",System.currentTimeMillis());
            db.update("entries",updated,"id=?",new String[]{entry.id});
            db.setTransactionSuccessful();
            return true;
        } finally { db.endTransaction(); }
    }

    static boolean insertPhoto(SQLiteDatabase db, String entry, String id, byte[] photo) {
        String[] args = {entry};
        long count = DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM photos WHERE entry_id=?",args);
        if (count >= MAX_PHOTOS || photo.length == 0 || photo.length > MAX_PHOTO_BYTES) return false;
        long last = DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(position),0) FROM photos WHERE entry_id=?",args);
        ContentValues values = new ContentValues();
        values.put("id",id);
        values.put("entry_id",entry);
        values.put("position",last + 1);
        values.put("data",photo);
        db.insertOrThrow("photos",null,values);
        return true;
    }

    boolean hasEntryOn(String date) {
        return DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT EXISTS(SELECT 1 FROM entries WHERE day=?)",
            new String[]{date}) != 0;
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("[\\p{M}ـ]", "")
            .replace('ى','ي').toLowerCase(Locale.ROOT).trim();
    }
}
