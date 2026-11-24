package app.alyawmiyat.journal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class EntryData {
    static final int PAPER_WIDTH = 800;
    static final int PAPER_HEIGHT = 1024;
    static final int MAX_POINTS = 24_000;
    static final int MAX_JSON = 1_000_000;
    static final int MAX_BODY = 80_000;
    static final int MAX_TITLE = 100;
    final String id;
    final String day;
    final String title;
    final String body;
    final String mode;
    final JSONArray ink;
    final long updated;

    EntryData(String id, String day, String title, String body, String mode, JSONArray ink, long updated) {
        this.id = id;
        this.day = day;
        this.title = title;
        this.body = body;
        this.mode = mode;
        this.ink = ink;
        this.updated = updated;
    }

    static EntryData emptyText(String id, String day) {
        return new EntryData(id, day, "", "", "text", new JSONArray(), System.currentTimeMillis());
    }

    static EntryData parse(String source) throws JSONException {
        if (source == null || source.length() > MAX_JSON) throw new JSONException("Entry too large");
        JSONObject json = new JSONObject(source);
        String id = json.getString("id");
        String day = json.getString("day");
        String title = json.getString("title");
        String body = json.getString("body");
        String mode = json.getString("mode");
        long updated = json.optLong("updated", 0);
        JSONArray ink = json.getJSONArray("ink");
        if (!validId(id) || !validDate(day) || title.length() > MAX_TITLE ||
            body.length() > MAX_BODY || updated < 0 ||
            (!mode.equals("ink") && !mode.equals("text"))) throw new JSONException("Invalid entry");
        if (mode.equals("text") && ink.length() > 0 || mode.equals("ink") && !body.isEmpty()) {
            throw new JSONException("Invalid entry mode");
        }
        int pointCount = 0;
        for (int i = 0; i < ink.length(); i++) {
            JSONArray stroke = ink.getJSONArray(i);
            if (stroke.length() < 2 || stroke.length() > 3 || !(stroke.get(0) instanceof Boolean) ||
                (stroke.length() == 3 && !inRange(stroke.get(2),2))) throw new JSONException("Invalid stroke");
            JSONArray points = stroke.getJSONArray(1);
            pointCount += points.length();
            if (points.length() == 0 || pointCount > MAX_POINTS) throw new JSONException("Oversized drawing");
            for (int j = 0; j < points.length(); j++) {
                JSONArray point = points.getJSONArray(j);
                if (point.length() != 3 || !inRange(point.get(0), PAPER_WIDTH) ||
                    !inRange(point.get(1), PAPER_HEIGHT) || !inRange(point.get(2), 100)) {
                    throw new JSONException("Invalid point");
                }
            }
        }
        return new EntryData(id,day,title,body,mode,ink,updated);
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("day", day).put("title", title)
            .put("body", body).put("mode", mode).put("ink", ink).put("updated", updated);
    }

    private static boolean inRange(Object value, int max) {
        if (!(value instanceof Number)) return false;
        double number = ((Number) value).doubleValue();
        return number >= 0 && number <= max && number == Math.floor(number);
    }

    static boolean validId(String id) {
        return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    static boolean validDate(String day) {
        if (day == null || !day.matches("(?:19[0-9]{2}|[2-9][0-9]{3})-[0-9]{2}-[0-9]{2}")) return false;
        int year = Integer.parseInt(day.substring(0, 4));
        int month = Integer.parseInt(day.substring(5, 7));
        int date = Integer.parseInt(day.substring(8, 10));
        if (month < 1 || month > 12 || date < 1) return false;
        boolean leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
        int[] days = {31,leap ? 29 : 28,31,30,31,30,31,31,30,31,30,31};
        return date <= days[month - 1];
    }
}
