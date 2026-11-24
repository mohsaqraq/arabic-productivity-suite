package app.altaqwim.calendar;

import org.json.JSONException;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

final class CalendarItem {
    private static final Pattern ID = Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    private static final Pattern CLOCK = Pattern.compile("(?:[01][0-9]|2[0-3]):[0-5][0-9]");
    final String id,kind,title,day,start,end,note;
    final boolean done;
    final int remind,priority;

    CalendarItem(String id,String kind,String title,String day,String start,String end,String note,boolean done,int remind,int priority) {
        this.id=id;
        this.kind=kind;
        this.title=title;
        this.day=day;
        this.start=start;
        this.end=end;
        this.note=note;
        this.done=done;
        this.remind=remind;
        this.priority=priority;
    }

    static CalendarItem parse(JSONObject source) throws JSONException {
        String id=source.getString("id"), kind=source.getString("kind"), title=source.getString("title").trim();
        String day=source.getString("day"), start=source.getString("start"), end=source.getString("end");
        String note=source.getString("note").trim();
        int remind=source.getInt("remind");
        Object rawPriority=source.opt("priority");
        double priority=rawPriority==null ? 0 : rawPriority instanceof Number ?
            ((Number) rawPriority).doubleValue() : -1;
        boolean done=source.getBoolean("done");
        if (!validId(id) || !validDay(day) || title.isEmpty() || title.length()>120 || note.length()>3000 ||
            (!(priority>=0 && priority<=3) || priority!=Math.floor(priority)) ||
            (!kind.equals("event") && !kind.equals("task")) ||
            (!start.isEmpty() && !CLOCK.matcher(start).matches()) ||
            (remind!=-1 && remind!=0 && remind!=10 && remind!=30 && remind!=60 && remind!=1440)) {
            throw new JSONException("Invalid item");
        }
        if (kind.equals("event")) {
            if (done || (start.isEmpty() ? !end.isEmpty() : !CLOCK.matcher(end).matches() || end.compareTo(start)<=0)) {
                throw new JSONException("Invalid event time");
            }
        } else if (!end.isEmpty()) throw new JSONException("Task cannot have an end time");
        return new CalendarItem(id,kind,title,day,start,end,note,done,remind,(int) priority);
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("id",id).put("kind",kind).put("title",title).put("day",day).put("start",start)
            .put("end",end).put("note",note).put("done",done).put("remind",remind).put("priority",priority);
    }

    Instant anchor(ZoneId zone) {
        return LocalDate.parse(day).atTime(start.isEmpty() ? LocalTime.of(9,0) : LocalTime.parse(start))
            .atZone(zone).toInstant();
    }

    static boolean validId(String id) { return id!=null && ID.matcher(id).matches(); }

    static boolean validDay(String value) {
        if (value==null || value.length()!=10) return false;
        try {
            LocalDate day=LocalDate.parse(value);
            return day.getYear()>=1900 && day.getYear()<=9998;
        } catch (DateTimeParseException e) { return false; }
    }
}
