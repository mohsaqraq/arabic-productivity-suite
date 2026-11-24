package app.almolahazat.notes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class Note {
    static final int PHOTO_LIMIT = 4;
    static final int PHOTO_BYTES = 620 * 1024;
    static final int MAX_JSON = 1_200_000;
    static final int BODY_LIMIT = 30000;
    static final int ITEMS_LIMIT = 150;
    static final String[] COLORS = {"paper","gold","peach","rose","mint","sky","lilac"};
    private static final Pattern ID = Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    private static final Pattern MARKS = Pattern.compile("[\\u0610-\\u061a\\u0640\\u064b-\\u065f\\u0670\\u06d6-\\u06ed]");
    private static final Pattern ALIFS = Pattern.compile("[أإآٱ]");
    String id, title, body, color, state, kind;
    long created, edited;
    boolean pinned;
    final List<Row> items = new ArrayList<>();
    final List<String> labels = new ArrayList<>();
    final List<String> photos = new ArrayList<>();

    static final class Row {
        final String text;
        final boolean done;
        Row(String text, boolean done) { this.text = text; this.done = done; }
    }

    static boolean validId(String value) { return value != null && ID.matcher(value).matches(); }

    static String fold(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return ALIFS.matcher(MARKS.matcher(normalized).replaceAll("")).replaceAll("ا")
            .replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي');
    }

    static String shorten(String value, int limit) {
        if (value.length() <= limit) return value;
        int end = Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit;
        return value.substring(0, end);
    }

    static String label(String value) {
        return shorten(value.replaceAll("[\\p{Cc}\\p{Zs}]+", " ").trim(), 40);
    }

    static Note parse(JSONObject object) throws JSONException {
        Note note = new Note();
        note.id = object.getString("id");
        if (!validId(note.id)) throw new JSONException("Invalid note identifier");
        note.title = checkLength(object.optString("title", ""), 200);
        note.body = checkLength(object.optString("body", ""), BODY_LIMIT);
        note.kind = object.optString("kind", "text");
        if (!"text".equals(note.kind) && !"list".equals(note.kind)) throw new JSONException("Invalid note kind");
        note.color = object.optString("color", "paper");
        boolean validColor = false;
        for (String color : COLORS) validColor |= color.equals(note.color);
        if (!validColor) throw new JSONException("Invalid note color");
        note.state = object.optString("state", "active");
        if (!"active".equals(note.state) && !"archive".equals(note.state) && !"trash".equals(note.state))
            throw new JSONException("Invalid note state");
        note.pinned = object.optBoolean("pinned") && !"archive".equals(note.state);
        long now = System.currentTimeMillis();
        note.created = timestamp(object.optLong("created"), now);
        note.edited = Math.max(note.created, timestamp(object.optLong("edited"), note.created));
        JSONArray items = array(object, "items", ITEMS_LIMIT);
        for (int i = 0; i < items.length(); i++) {
            JSONObject row = items.getJSONObject(i);
            String value = checkLength(row.getString("text"), 1000);
            if (!value.trim().isEmpty()) note.items.add(new Row(value, row.optBoolean("done")));
        }
        JSONArray labels = array(object, "labels", 16);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < labels.length(); i++) {
            String text = label(labels.getString(i));
            if (!text.isEmpty() && keys.add(fold(text))) note.labels.add(text);
        }
        JSONArray photos = array(object, "photos", PHOTO_LIMIT);
        for (int i = 0; i < photos.length(); i++) {
            String photo = photos.getString(i);
            if (!validId(photo) || note.photos.contains(photo)) throw new JSONException("Invalid photo identifier");
            note.photos.add(photo);
        }
        return note;
    }

    private static JSONArray array(JSONObject object, String name, int max) throws JSONException {
        JSONArray values = object.has(name) ? object.getJSONArray(name) : new JSONArray();
        if (values.length() > max) throw new JSONException("Too many values: " + name);
        return values;
    }

    private static long timestamp(long value, long fallback) {
        return value > 0 && value <= 253402300799999L ? value : fallback;
    }

    private static String checkLength(String value, int max) throws JSONException {
        if (value.length() > max) throw new JSONException("Text too long");
        return value;
    }

    boolean meaningful() {
        return !title.trim().isEmpty() || !body.trim().isEmpty() || !items.isEmpty() || !photos.isEmpty();
    }

    JSONObject json(boolean preview) throws JSONException {
        JSONArray rows = new JSONArray();
        for (int i = 0; i < (preview ? Math.min(5, items.size()) : items.size()); i++) {
            Row row = items.get(i);
            rows.put(new JSONObject().put("text", preview ? shorten(row.text, 135) : row.text).put("done", row.done));
        }
        JSONObject result = new JSONObject().put("id", id).put("title", title)
            .put("body", preview ? shorten(body, 330) : body).put("color", color)
            .put("state", state).put("kind", kind).put("pinned", pinned).put("created", created)
            .put("edited", edited).put("labels", new JSONArray(labels)).put("photos", new JSONArray(photos)).put("items", rows);
        if (preview) result.put("total", items.size()).put("doneCount", items.stream().filter(row -> row.done).count());
        return result;
    }
}
