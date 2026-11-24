package app.almolahazat.notes;

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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class NotesFiles {
    static final String FORMAT = "almolahazat";
    private static final long TOTAL_BYTES = 512L * 1024 * 1024;

    private NotesFiles() {}

    static void export(NotesStore store, OutputStream output, boolean markdown) throws JSONException, IOException {
        SQLiteDatabase database = store.getReadableDatabase();
        database.beginTransaction();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (!markdown) write(zip, "format.json", bytes(new JSONObject().put("format", FORMAT).put("version", 1).toString()));
            try (Cursor cursor = database.query("notes", new String[]{"data"}, markdown ? "state <> 'trash'" : null,
                null, null, null, "created,id")) {
                while (cursor.moveToNext()) {
                    Note note = Note.parse(new JSONObject(cursor.getString(0)));
                    if (markdown) {
                        String file = note.title.trim().replaceAll("[^\\p{L}\\p{M}\\p{N} _-]", "_");
                        file = Note.shorten(file.isEmpty() ? "ملاحظة" : file, 50);
                        write(zip, file + "-" + note.id + ".md", bytes(markdown(note)));
                    } else write(zip, "notes/" + note.id + ".json", bytes(note.json(false).toString()));
                    for (String photo : note.photos) {
                        byte[] image = NotesStore.photo(database, photo);
                        if (image == null) throw new IOException("Missing attachment");
                        write(zip, "photos/" + photo + ".jpg", image);
                    }
                }
            }
            zip.finish();
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static void write(ZipOutputStream zip, String path, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }

    static String markdown(Note note) {
        StringBuilder text = new StringBuilder();
        if (!note.title.trim().isEmpty()) text.append("# ").append(note.title.replaceAll("[\\r\\n]+", " ")).append("\n\n");
        if (!note.body.isEmpty()) text.append(note.body).append("\n\n");
        if ("list".equals(note.kind)) {
            for (Note.Row row : note.items) {
                text.append(row.done ? "- [x] " : "- [ ] ").append(row.text.replace("\n", "\n  ")).append('\n');
            }
            text.append('\n');
        }
        if (!note.labels.isEmpty()) text.append("الوسوم: ").append(String.join("، ", note.labels)).append("\n\n");
        for (String photo : note.photos) text.append("![صورة](photos/").append(photo).append(".jpg)\n");
        return text.toString();
    }

    static final class ImportResult {
        int added, existing, attachments;
    }

    static ImportResult restore(NotesStore store, InputStream input, boolean keep) throws IOException, JSONException {
        SQLiteDatabase database = store.getWritableDatabase();
        database.beginTransaction();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ImportResult result = keep ? importKeep(store, database, new Archive(zip))
                : restoreBackup(store, database, new Archive(zip));
            database.setTransactionSuccessful();
            return result;
        } finally { database.endTransaction(); }
    }

    private static ImportResult restoreBackup(NotesStore store, SQLiteDatabase database, Archive archive)
        throws IOException, JSONException {
        if (!"format.json".equals(archive.next())) throw new IOException("Missing backup header");
        JSONObject header = archive.json(1024);
        if (!FORMAT.equals(header.optString("format")) || header.optInt("version") != 1)
            throw new IOException("Unsupported backup");
        ImportResult result = new ImportResult();
        Set<String> seen = new HashSet<>();
        String path;
        while ((path = archive.next()) != null) {
            if (!path.startsWith("notes/") || !path.endsWith(".json")) throw new IOException("Unexpected backup entry");
            Note note = Note.parse(archive.json(Note.MAX_JSON));
            if (!path.equals("notes/" + note.id + ".json") || !seen.add(note.id)) throw new IOException("Invalid note path");
            boolean insert = store.get(database, note.id) == null;
            if (insert) store.write(database, note);
            for (String photo : note.photos) {
                if (!("photos/" + photo + ".jpg").equals(archive.next())) throw new IOException("Missing photo");
                byte[] image = archive.read(Note.PHOTO_BYTES, true);
                if (image.length < 4 || image[0] != (byte) 0xff || image[1] != (byte) 0xd8)
                    throw new IOException("Invalid image");
                if (insert) NotesStore.putPhoto(database, note.id, photo, image);
            }
            if (insert) result.added++; else result.existing++;
        }
        return result;
    }

    private static ImportResult importKeep(NotesStore store, SQLiteDatabase database, Archive archive)
        throws IOException, JSONException {
        ImportResult result = new ImportResult();
        int recognized = 0;
        String path;
        while ((path = archive.next()) != null) {
            if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                archive.read(TOTAL_BYTES, false);
                continue;
            }
            byte[] content = archive.read(Note.MAX_JSON, true);
            JSONObject raw;
            try { raw = new JSONObject(new String(content, StandardCharsets.UTF_8)); }
            catch (JSONException ignored) { continue; }
            if (!raw.has("createdTimestampUsec") || !(raw.has("textContent") || raw.has("listContent")
                || raw.has("title") || raw.has("attachments"))) continue;
            recognized++;
            if (raw.optBoolean("isTrashed")) continue;
            JSONArray attachments = raw.optJSONArray("attachments");
            if (attachments != null) result.attachments += attachments.length();
            Note note = keepNote(path, raw);
            if (!note.meaningful()) continue;
            if (store.get(database, note.id) != null) result.existing++;
            else { store.write(database, note); result.added++; }
        }
        if (recognized == 0) throw new IOException("No Keep notes");
        return result;
    }

    private static Note keepNote(String path, JSONObject raw) throws JSONException {
        long created = raw.optLong("createdTimestampUsec") / 1000;
        long edited = raw.optLong("userEditedTimestampUsec") / 1000;
        // Use the original creation timestamp independently of the archive root.
        String identity = created > 0 ? raw.optString("createdTimestampUsec")
            : path.substring(path.lastIndexOf('/') + 1);
        String id = UUID.nameUUIDFromBytes(bytes("google-keep:" + identity)).toString();
        JSONArray rows = raw.optJSONArray("listContent");
        JSONArray items = new JSONArray();
        if (rows != null) {
            for (int i = 0; i < Math.min(rows.length(), Note.ITEMS_LIMIT); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item != null) items.put(new JSONObject().put("text", Note.shorten(item.optString("text"), 1000))
                    .put("done", item.optBoolean("isChecked")));
            }
        }
        JSONArray rawLabels = raw.optJSONArray("labels");
        JSONArray labels = new JSONArray();
        if (rawLabels != null) {
            for (int i = 0; i < Math.min(rawLabels.length(), 16); i++) {
                JSONObject label = rawLabels.optJSONObject(i);
                if (label != null) labels.put(label.optString("name"));
            }
        }
        return Note.parse(new JSONObject().put("id", id).put("title", Note.shorten(raw.optString("title"), 200))
            .put("body", Note.shorten(raw.optString("textContent"), Note.BODY_LIMIT))
            .put("state", raw.optBoolean("isArchived") ? "archive" : "active").put("pinned", raw.optBoolean("isPinned"))
            .put("kind", rows == null ? "text" : "list").put("color", keepColor(raw.optString("color")))
            .put("created", created).put("edited", edited).put("labels", labels).put("items", items));
    }

    private static String keepColor(String color) {
        switch (color) {
            case "YELLOW": case "SAND": return "gold";
            case "ORANGE": case "CORAL": return "peach";
            case "RED": case "PINK": return "rose";
            case "GREEN": case "TEAL": case "SAGE": return "mint";
            case "BLUE": case "DARK_BLUE": case "FOG": return "sky";
            case "PURPLE": case "DUSK": return "lilac";
            default: return "paper";
        }
    }

    private static final class Archive {
        final ZipInputStream input;
        long readTotal;
        int entries;
        Archive(ZipInputStream input) { this.input = input; }

        String next() throws IOException {
            ZipEntry entry = input.getNextEntry();
            if (entry == null) return null;
            if (++entries > 25000) throw new IOException("Archive has too many entries");
            return entry.getName();
        }

        JSONObject json(int limit) throws IOException, JSONException {
            return new JSONObject(new String(read(limit, true), StandardCharsets.UTF_8));
        }

        byte[] read(long limit, boolean collect) throws IOException {
            ByteArrayOutputStream output = collect ? new ByteArrayOutputStream() : null;
            byte[] buffer = new byte[8192];
            int count;
            long size = 0;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                readTotal += count;
                if (size > limit || readTotal > TOTAL_BYTES) throw new IOException("Archive size limit");
                if (collect) output.write(buffer, 0, count);
            }
            return collect ? output.toByteArray() : null;
        }
    }
}
