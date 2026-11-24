package app.almolahazat.notes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Locale;

final class NotesStore extends SQLiteOpenHelper {
    static final int PAGE = 36;

    NotesStore(Context context) { super(context, "notes.db", null, 1); }

    @Override public void onConfigure(SQLiteDatabase database) {
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE notes(id TEXT PRIMARY KEY, data TEXT NOT NULL, preview TEXT NOT NULL, "
            + "search TEXT NOT NULL, tag_keys TEXT NOT NULL, labels TEXT NOT NULL, kind TEXT NOT NULL, "
            + "has_photo INTEGER NOT NULL, state TEXT NOT NULL, pinned INTEGER NOT NULL, "
            + "created INTEGER NOT NULL, edited INTEGER NOT NULL, title_key TEXT NOT NULL)");
        database.execSQL("CREATE INDEX note_edited ON notes(state,pinned DESC,edited DESC)");
        database.execSQL("CREATE TABLE photos(id TEXT PRIMARY KEY, note_id TEXT NOT NULL "
            + "REFERENCES notes(id) ON DELETE CASCADE, content BLOB NOT NULL)");
        database.execSQL("CREATE INDEX note_photos ON photos(note_id)");
    }

    @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unexpected schema version");
    }

    String list(String state, String type, String label, String query, String order, int offset) throws JSONException {
        if (!"active".equals(state) && !"archive".equals(state) && !"trash".equals(state)) return "[]";
        String condition = "state = ? AND instr(search,?) > 0 AND instr(tag_keys,?) > 0";
        if ("list".equals(type)) condition += " AND kind = 'list'";
        if ("photo".equals(type)) condition += " AND has_photo = 1";
        String orderBy;
        switch (order) {
            case "created": orderBy = "created DESC, edited DESC"; break;
            case "title": orderBy = "title_key COLLATE NOCASE ASC, edited DESC"; break;
            default: orderBy = "edited DESC, created DESC"; break;
        }
        String[] args = {state, Note.fold(Note.shorten(query.trim(), 180)),
            label.isEmpty() ? "" : "\n" + Note.fold(Note.label(label)) + "\n"};
        try (Cursor cursor = getReadableDatabase().query("notes", new String[]{"preview"}, condition, args,
            null, null, "pinned DESC," + orderBy + ",id", (PAGE + 1) + " OFFSET " + Math.max(0, offset))) {
            JSONArray result = new JSONArray();
            while (cursor.moveToNext()) result.put(new JSONObject(cursor.getString(0)));
            return result.toString();
        }
    }

    String labels(String state) throws JSONException {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try (Cursor cursor = getReadableDatabase().query("notes", new String[]{"labels"}, "state = ?",
            new String[]{state}, null, null, "edited DESC")) {
            while (cursor.moveToNext()) {
                JSONArray labels = new JSONArray(cursor.getString(0));
                for (int i = 0; i < labels.length(); i++) {
                    String name = labels.getString(i);
                    result.putIfAbsent(Note.fold(name), name);
                }
            }
        }
        return new JSONArray(result.values()).toString();
    }

    Note get(String id) throws JSONException { return get(getReadableDatabase(), id); }

    Note get(SQLiteDatabase database, String id) throws JSONException {
        if (!Note.validId(id)) return null;
        try (Cursor cursor = database.query("notes", new String[]{"data"}, "id = ?",
            new String[]{id}, null, null, null)) {
            return cursor.moveToFirst() ? Note.parse(new JSONObject(cursor.getString(0))) : null;
        }
    }

    void save(Note note) throws JSONException {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            Note old = get(database, note.id);
            if (old == null && !note.meaningful()) {
                database.setTransactionSuccessful();
                return;
            }
            for (String photo : note.photos) {
                if (old == null || !old.photos.contains(photo)) throw new JSONException("Unknown photo");
            }
            if (old != null) {
                note.created = old.created;
                for (String photo : old.photos) {
                    if (!note.photos.contains(photo)) database.delete("photos", "note_id = ? AND id = ?",
                        new String[]{note.id, photo});
                }
            }
            note.edited = Math.max(System.currentTimeMillis(), note.created);
            write(database, note);
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    Note attach(Note draft, String photo, byte[] bytes) throws JSONException {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            Note existing = get(database, draft.id);
            Note note = existing != null ? existing : draft;
            if (existing == null && !draft.photos.isEmpty()) throw new JSONException("Unknown photos");
            if ("trash".equals(note.state) || note.photos.size() >= Note.PHOTO_LIMIT) throw new JSONException("Photo limit");
            note.photos.add(photo);
            note.edited = Math.max(System.currentTimeMillis(), note.created);
            write(database, note);
            putPhoto(database, note.id, photo, bytes);
            database.setTransactionSuccessful();
            return note;
        } finally { database.endTransaction(); }
    }

    boolean erase(String id) {
        return Note.validId(id) && getWritableDatabase().delete("notes", "id = ? AND state = 'trash'",
            new String[]{id}) == 1;
    }

    void write(SQLiteDatabase database, Note note) throws JSONException {
        ContentValues values = new ContentValues();
        values.put("id", note.id);
        values.put("data", note.json(false).toString());
        values.put("preview", note.json(true).toString());
        values.put("labels", new JSONArray(note.labels).toString());
        StringBuilder tags = new StringBuilder("\n");
        StringBuilder search = new StringBuilder(note.title).append('\n').append(note.body);
        for (String label : note.labels) {
            tags.append(Note.fold(label)).append('\n');
            search.append('\n').append(label);
        }
        for (Note.Row row : note.items) search.append('\n').append(row.text);
        values.put("tag_keys", tags.toString());
        values.put("search", Note.fold(search.toString()));
        values.put("title_key", note.title.trim().isEmpty() ? "\uffff" : Note.fold(note.title.trim()).toLowerCase(Locale.ROOT));
        values.put("kind", note.kind);
        values.put("has_photo", note.photos.isEmpty() ? 0 : 1);
        values.put("state", note.state);
        values.put("pinned", note.pinned ? 1 : 0);
        values.put("created", note.created);
        values.put("edited", note.edited);
        if (database.update("notes", values, "id = ?", new String[]{note.id}) == 0)
            database.insertOrThrow("notes", null, values);
    }

    static void putPhoto(SQLiteDatabase database, String note, String photo, byte[] content) {
        if (content.length == 0 || content.length > Note.PHOTO_BYTES) throw new IllegalArgumentException("Photo size");
        ContentValues values = new ContentValues();
        values.put("id", photo);
        values.put("note_id", note);
        values.put("content", content);
        database.insertOrThrow("photos", null, values);
    }

    byte[] photo(String id) { return photo(getReadableDatabase(), id); }

    static byte[] photo(SQLiteDatabase database, String id) {
        if (!Note.validId(id)) return null;
        try (Cursor cursor = database.query("photos", new String[]{"content"}, "id = ?", new String[]{id},
            null, null, null)) {
            return cursor.moveToFirst() ? cursor.getBlob(0) : null;
        }
    }
}
