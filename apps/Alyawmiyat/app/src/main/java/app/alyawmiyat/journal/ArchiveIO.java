package app.alyawmiyat.journal;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class ArchiveIO {
    private static final String HEADER = "{\"format\":\"alyawmiyat\",\"version\":2}";
    private static final int MAX_ENTRIES = 30_000;
    private static final long MAX_TOTAL_BYTES = 1_500L * 1024 * 1024;

    private ArchiveIO() {}

    static void exportBackup(JournalStore store, OutputStream target) throws IOException, JSONException {
        SQLiteDatabase db = store.getReadableDatabase();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(target))) {
            db.beginTransactionNonExclusive();
            try (Cursor rows = db.rawQuery("SELECT id FROM entries ORDER BY day DESC,updated DESC,id",null)) {
                if (rows.getCount() > MAX_ENTRIES) throw new IOException("Archive limit");
                long total = put(zip, "journal.json", bytes(HEADER));
                int photos = 0;
                while (rows.moveToNext()) {
                    String id = rows.getString(0);
                    EntryData entry = JournalStore.load(db,id);
                    JSONObject data = entry.json().put("revisit",JournalStore.revisit(db,id));
                    total += put(zip, "entries/" + id + ".json", bytes(data.toString()));
                    for (String photo : JournalStore.photoIds(db,id)) {
                        total += put(zip,"photos/" + id + "/" + photo + ".jpg",JournalStore.photo(db,photo));
                        photos++;
                    }
                    if (total > MAX_TOTAL_BYTES) throw new IOException("Archive limit");
                }
                String footer = new JSONObject().put("entries",rows.getCount()).put("photos",photos).toString();
                put(zip,"complete.json",bytes(footer));
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }

    static int importBackup(JournalStore store, InputStream source) throws IOException, JSONException {
        SQLiteDatabase db = store.getWritableDatabase();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry header = zip.getNextEntry();
            if (header == null || !"journal.json".equals(header.getName())) throw new IOException("Wrong file");
            JSONObject metadata = new JSONObject(decode(read(zip,1024)));
            int version = metadata.optInt("version");
            if (!"alyawmiyat".equals(metadata.optString("format")) || (version != 1 && version != 2)) {
                throw new IOException("Unsupported archive");
            }
            Set<String> ids = new HashSet<>();
            long total = 0;
            int photos = 0;
            boolean complete = false;
            db.beginTransaction();
            try {
                db.delete("entries",null,null);
                ZipEntry part;
                while ((part = zip.getNextEntry()) != null) {
                    String path = part.getName();
                    if (complete) throw new IOException("Trailing archive data");
                    if ("complete.json".equals(path)) {
                        JSONObject footer = new JSONObject(decode(read(zip,1024)));
                        if (footer.getInt("entries") != ids.size() || footer.getInt("photos") != photos) {
                            throw new IOException("Incomplete archive");
                        }
                        complete = true;
                    } else if (path.length() == 49 && path.startsWith("entries/") && path.endsWith(".json")) {
                        byte[] contents = read(zip, EntryData.MAX_JSON * 3);
                        total += contents.length;
                        String sourceEntry = decode(contents);
                        EntryData entry = EntryData.parse(sourceEntry);
                        if (!path.equals("entries/" + entry.id + ".json") ||
                            !ids.add(entry.id) || ids.size() > MAX_ENTRIES) throw new IOException("Duplicate entry");
                        JournalStore.write(db,entry);
                        if (version == 2) restoreRevisit(db,entry,new JSONObject(sourceEntry));
                    } else if (path.length() == 84 && path.startsWith("photos/") && path.endsWith(".jpg")) {
                        String entryId = path.substring(7,43);
                        String photoId = path.substring(44,80);
                        if (path.charAt(43) != '/' || !ids.contains(entryId) || !EntryData.validId(photoId)) {
                            throw new IOException("Orphan attachment");
                        }
                        EntryData entry = JournalStore.load(db,entryId);
                        byte[] photo = read(zip, JournalStore.MAX_PHOTO_BYTES);
                        total += photo.length;
                        if (entry == null || !entry.mode.equals("text") || !PhotoCodec.validBackupPhoto(photo) ||
                            !JournalStore.insertPhoto(db,entryId,photoId,photo)) throw new IOException("Invalid attachment");
                        photos++;
                    } else throw new IOException("Unknown record");
                    if (total > MAX_TOTAL_BYTES) throw new IOException("Archive limit");
                }
                if (!complete) throw new IOException("Missing completion marker");
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            return ids.size();
        }
    }

    private static void restoreRevisit(SQLiteDatabase db,EntryData entry,JSONObject source) throws JSONException {
        if (source.isNull("revisit")) return;
        JSONObject reminder = source.getJSONObject("revisit");
        String period = reminder.getString("period");
        boolean delivered = reminder.getBoolean("delivered");
        String savedDate = reminder.getString("due");
        String expected = RevisitReminders.dueDate(entry.day,period);
        if (expected.isEmpty() || !EntryData.validDate(savedDate) || (!delivered && !expected.equals(savedDate))) {
            throw new JSONException("Invalid revisit");
        }
        JournalStore.writeRevisit(db,entry.id,period,savedDate,delivered);
    }

    static void exportReadable(JournalStore store, OutputStream target, String entryId) throws IOException, JSONException {
        SQLiteDatabase db = store.getReadableDatabase();
        String where = entryId.isEmpty() ? "" : " WHERE id=?";
        String[] args = entryId.isEmpty() ? null : new String[]{entryId};
        try (BufferedOutputStream out = new BufferedOutputStream(target)) {
            out.write(bytes("<!doctype html><html lang=ar dir=rtl><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<title>اليوميات</title><style>body{margin:auto;max-width:760px;padding:20px;font:16px/1.85 system-ui,sans-serif;background:#f5f8f4;color:#173b32}"
                + "article{background:white;border:1px solid #dbe9df;border-radius:16px;margin:14px 0;padding:16px;break-inside:avoid}h2{margin:2px 0}"
                + "p{white-space:pre-wrap;overflow-wrap:anywhere}time{color:#4b6c61}img{display:block;max-width:100%;height:auto;margin:12px 0;border-radius:10px}</style>"
                + "<body><h1>اليوميات</h1>"));
            db.beginTransactionNonExclusive();
            try (Cursor rows = db.rawQuery("SELECT id FROM entries" + where + " ORDER BY day DESC,updated DESC,id",args)) {
                while (rows.moveToNext()) {
                    String id = rows.getString(0);
                    EntryData entry = JournalStore.load(db,id);
                    out.write(bytes("<article><time datetime=\"" + entry.day + "\">" + entry.day + "</time><h2>" +
                        escape(entry.title.trim().isEmpty() ? "يومية" : entry.title) + "</h2>"));
                    if (!entry.body.trim().isEmpty()) out.write(bytes("<p dir=\"rtl\">" + escape(entry.body) + "</p>"));
                    if (entry.ink.length() > 0) writeImage(out,"image/png",inkPng(entry.ink),"يومية بخط اليد");
                    for (String photo : JournalStore.photoIds(db,id)) {
                        writeImage(out,"image/jpeg",JournalStore.photo(db,photo),"صورة مرفقة");
                    }
                    out.write(bytes("</article>"));
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            out.write(bytes("</body></html>"));
        }
    }

    private static byte[] inkPng(JSONArray ink) throws JSONException, IOException {
        Bitmap bitmap = Bitmap.createBitmap(EntryData.PAPER_WIDTH, EntryData.PAPER_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
        brush.setColor(0xff193d35);
        brush.setStrokeCap(Paint.Cap.ROUND);
        PorterDuffXfermode clear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
        try (ByteArrayOutputStream png = new ByteArrayOutputStream()) {
            for (int i = 0; i < ink.length(); i++) {
                JSONArray stroke = ink.getJSONArray(i);
                boolean erase = stroke.getBoolean(0);
                int size = stroke.optInt(2,1);
                brush.setXfermode(erase ? clear : null);
                JSONArray points = stroke.getJSONArray(1);
                JSONArray previous = points.getJSONArray(0);
                canvas.drawCircle(previous.getInt(0),previous.getInt(1),width(erase,size,previous,previous) / 2,brush);
                for (int j = 1; j < points.length(); j++) {
                    JSONArray point = points.getJSONArray(j);
                    brush.setStrokeWidth(width(erase,size,previous,point));
                    canvas.drawLine(previous.getInt(0),previous.getInt(1),point.getInt(0),point.getInt(1),brush);
                    previous = point;
                }
            }
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) throw new IOException("Cannot render drawing");
            return png.toByteArray();
        } finally { bitmap.recycle(); }
    }

    private static float width(boolean erase, int size, JSONArray first, JSONArray second) throws JSONException {
        if (erase) return 24f;
        float multiplier = size == 0 ? .65f : size == 2 ? 1.8f : 1f;
        return (1.6f + (first.getInt(2) + second.getInt(2)) / 200f * 5.2f) * multiplier;
    }

    private static void writeImage(OutputStream out,String mime,byte[] data,String alt) throws IOException {
        out.write(bytes("<img loading=lazy alt=\"" + alt + "\" src=\"data:" + mime + ";base64,"));
        out.write(Base64.encode(data,Base64.NO_WRAP));
        out.write(bytes("\">"));
    }

    static String escape(String value) {
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
            .replace("\"","&quot;").replace("'","&#39;");
    }

    static byte[] read(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (bytes.size() + read > maximum) throw new IOException("File too large");
            bytes.write(buffer,0,read);
        }
        return bytes.toByteArray();
    }

    private static int put(ZipOutputStream zip,String name,byte[] value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value);
        zip.closeEntry();
        return value.length;
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String decode(byte[] value) { return new String(value,StandardCharsets.UTF_8); }
}
