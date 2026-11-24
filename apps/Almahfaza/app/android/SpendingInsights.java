package app.almahfaza.wallet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap;

final class SpendingInsights {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long DAY_MS = 86_400_000L;
    private static final long MAX_CENTS = 999_999_999_999L;
    private static final String[] IDS = {"food", "transport", "market", "bill", "health", "study", "play", "other"};
    private static final String[] LABELS = {"أكل وشرب", "مواصلات", "تسوّق", "فواتير", "صحة", "دراسة", "ترفيه", "أخرى"};
    private static final int[] FLEXIBLE = {0, 1, 2, 6, 7};

    static final class Insight {
        final String kind;
        final String category;
        final String month;
        final String title;
        final String body;
        final int priority;

        Insight(String kind, String category, String month, String title, String body, int priority) {
            this.kind = kind;
            this.category = category;
            this.month = month;
            this.title = title;
            this.body = body;
            this.priority = priority;
        }

        boolean oncePerMonth() {
            return kind.equals("budget-over") || kind.equals("budget-near") ||
                kind.equals("budget-pace") || kind.equals("month-close");
        }
    }

    private static final class Day {
        long total;
        final long[] amounts = new long[IDS.length];
        final int[] counts = new int[IDS.length];
        final long[] largest = new long[IDS.length];

        void add(int category, long cents) {
            total += cents;
            amounts[category] += cents;
            counts[category]++;
            largest[category] = Math.max(largest[category], cents);
        }
    }

    private static final class Period {
        long total;
        int days;
        final long[] amounts = new long[IDS.length];
        final int[] counts = new int[IDS.length];
        final int[] categoryDays = new int[IDS.length];
        final long[] largest = new long[IDS.length];

        void add(Day item) {
            total += item.total;
            days++;
            for (int category = 0; category < IDS.length; category++) {
                amounts[category] += item.amounts[category];
                counts[category] += item.counts[category];
                if (item.counts[category] > 0) categoryDays[category]++;
                largest[category] = Math.max(largest[category], item.largest[category]);
            }
        }

        int leadingCategory() {
            int leader = 0;
            for (int category = 1; category < IDS.length; category++) {
                if (amounts[category] > amounts[leader]) leader = category;
            }
            return leader;
        }
    }

    private final TreeMap<Integer, Day> data = new TreeMap<>();
    private final JSONObject budgets;
    private final DecimalFormat moneyFormat = new DecimalFormat(
        "#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private final String currency;
    private final int today;
    private final int yesterday;
    private final String focusMonth;
    private final int monthStart;
    private final Day latest;
    private final List<Insight> candidates = new ArrayList<>();

    private SpendingInsights(JSONObject state, int today) {
        this.today = today;
        yesterday = today - 1;
        focusMonth = iso(yesterday).substring(0, 7);
        monthStart = civilDay(focusMonth + "-01");
        budgets = state.optJSONObject("budgets");
        currency = currencySymbol(state.optString("currency", "SYP"));
        JSONArray records = state.optJSONArray("records");
        if (records != null) {
            SimpleDateFormat parser = dateFormat();
            String cutoff = iso(today - 62);
            String through = iso(yesterday);
            List<String> categoryIds = Arrays.asList(IDS);
            for (int index = 0; index < records.length(); index++) {
                JSONObject record = records.optJSONObject(index);
                if (record == null) continue;
                String date = record.optString("date");
                if (date.compareTo(cutoff) < 0 || date.compareTo(through) > 0) continue;
                int day = parseDay(parser, date);
                int cat = categoryIds.indexOf(record.optString("cat"));
                long amount = record.optLong("cents", 0);
                if (day == Integer.MIN_VALUE || day < today - 62 || day > yesterday ||
                    cat < 0 || amount <= 0 || amount > MAX_CENTS) continue;
                Day item = data.get(day);
                if (item == null) {
                    item = new Day();
                    data.put(day, item);
                }
                item.add(cat, amount);
            }
        }
        latest = data.get(yesterday);
    }

    static List<Insight> evaluate(String state, int today) {
        try {
            JSONObject parsed = new JSONObject(state);
            if (parsed.optInt("version") != 1) return Collections.emptyList();
            return new SpendingInsights(parsed, today).review();
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
    }

    private List<Insight> review() {
        checkBudget();
        checkClosedMonth();
        if (latest != null) checkYesterday();
        checkWeek();
        candidates.sort(Comparator.comparingInt((Insight candidate) -> candidate.priority).reversed());
        return candidates;
    }

    private void checkBudget() {
        long budget = budget(focusMonth);
        if (budget <= 0 || !focusMonth.equals(iso(today).substring(0, 7))) return;
        Period month = between(monthStart, yesterday);
        Period beforeRecent = between(monthStart, yesterday - 2);
        int used = percent(month.total, budget);
        if (month.total > budget && beforeRecent.total <= budget) {
            String excess = money(month.total - budget);
            add("budget-over", "", focusMonth, 120,
                "تجاوزت ميزانية الشهر", "المصاريف المسجّلة تجاوزت السقف بـ " + excess +
                ". ألقِ نظرة على أكبر فئة قبل المصروف القادم.",
                "السقف الشهري اتجاوز", "صار إجمالي الشهر " + money(month.total) +
                " مقابل سقف " + money(budget) + ". راجع المصروفات الأكبر في التحليلات.");
        } else if (month.total >= budget * 0.80 && month.total <= budget && beforeRecent.total < budget * 0.80) {
            String left = money(budget - month.total);
            add("budget-near", "", focusMonth, 111,
                month.total == budget ? "بلغت سقف الشهر" : "اقتربت من سقف الشهر", "استخدمت " + used + "٪ من ميزانيتك. بقي منها " + left + ".",
                "نظرة سريعة على ميزانيتك", "المتبقّي من السقف " + left + " بعد تسجيل " + used + "٪ من الميزانية.");
        }
        int monthDay = yesterday - monthStart + 1;
        int duration = monthLength(yesterday);
        int elapsed = percent(monthDay, duration);
        if (latest != null && month.days >= 5 && monthDay >= 5 && monthDay <= 25 &&
            used >= 45 && used < 80 && used - elapsed >= 22) {
            add("budget-pace", "", focusMonth, 101,
                "الصرف أسرع من مرور الشهر", "مرّ " + elapsed + "٪ من الشهر وبلغ الصرف " + used +
                "٪ من الميزانية. راجع ما يمكنك تأجيله إذا لزم.",
                "ميزانية الشهر تستحق نظرة", "وصلت إلى " + used + "٪ من السقف بعد " +
                monthDay + " يومًا. بقي " + money(budget - month.total) + " حتى نهاية الشهر.");
        }
    }

    private void checkClosedMonth() {
        String newMonth = iso(today).substring(0, 7);
        int start = civilDay(newMonth + "-01");
        if (today - start > 2) return;
        String closedMonth = iso(start - 1).substring(0, 7);
        Period closed = between(civilDay(closedMonth + "-01"), start - 1);
        if (closed.days < 3) return;
        long planned = budget(closedMonth);
        String sum = money(closed.total);
        if (planned > 0 && closed.total <= planned) {
            add("month-close", "", closedMonth, 106,
                "أغلقت الشهر ضمن السقف", "بلغ مصروف الشهر الماضي " + sum + " وبقي " +
                money(planned - closed.total) + " من ميزانيته.",
                "حصيلة شهر ضمن الميزانية", "سجّلت " + sum + " خلال الشهر الماضي، مقابل سقف " + money(planned) + ".");
            return;
        }
        String lead = LABELS[closed.leadingCategory()];
        add("month-close", "", closedMonth, 106,
            "ملخّص الشهر الماضي جاهز", "سجّلت " + sum + " وكانت «" + lead + "» أكبر فئة.",
            "كيف كان صرف الشهر الماضي؟", "المجموع المسجّل " + sum + "، وتصدّرت «" + lead + "» قائمة الفئات.");
    }

    private void checkYesterday() {
        List<Long> previousTotals = new ArrayList<>();
        for (Day day : data.subMap(yesterday - 28, true, yesterday, false).values()) {
            previousTotals.add(day.total);
        }
        long dayMedian = median(previousTotals);
        if (previousTotals.size() >= 7 && latest.total >= dayMedian * 1.9) {
            int rise = percent(latest.total - dayMedian, dayMedian);
            add("daily-total", "", focusMonth, 86,
                "أمس كان يوم صرف أعلى", "سجّلت " + money(latest.total) + "، أعلى بـ " +
                rise + "٪ من وسيط أيامك المسجّلة خلال الأسابيع الأربعة السابقة.",
                "مصروف أمس يستحق نظرة", "بلغ صرف أمس " + money(latest.total) + " مقابل " +
                money(dayMedian) + " في يوم تسجيل معتاد لديك.");
        }

        for (int category : FLEXIBLE) {
            if (latest.counts[category] == 0) continue;
            List<Long> history = new ArrayList<>();
            for (Day day : data.subMap(yesterday - 28, true, yesterday, false).values()) {
                if (day.counts[category] > 0) history.add(day.amounts[category]);
            }
            int count = history.size();
            long normal = median(history);
            long amount = latest.amounts[category];
            if (count < 4 || amount < normal * 1.85 || amount < dayMedian * 0.4) continue;
            String label = LABELS[category];
            String main = "سجّلت " + money(amount) + " أمس في «" + label + "»، مقابل " +
                money(normal) + " عادةً في أيام الصرف على هذه الفئة.";
            String alternative = "مصروف أمس على «" + label + "» أعلى بـ " + percent(amount - normal, normal) +
                "٪ من معتادك المسجّل لهذه الفئة. راجعه قبل التسجيل القادم.";
            if (latest.counts[category] == 1) {
                alternative = "عملية واحدة بقيمة " + money(amount) + " في «" + label +
                    "» أمس، مقابل " + money(normal) + " في يوم صرف معتاد على الفئة.";
            }
            int priority = 90 + Math.min(5, percent(amount - normal, normal) / 40);
            add("daily-category", IDS[category], focusMonth, priority,
                "ارتفاع في مصروف «" + label + "»", main, "«" + label + "» لفتت الانتباه أمس", alternative);
        }
    }

    private void checkWeek() {
        Period recent = between(yesterday - 6, yesterday);
        Period previous = between(yesterday - 13, yesterday - 7);
        if (recent.days < 3) return;
        for (int category : FLEXIBLE) {
            String label = LABELS[category];
            long amount = recent.amounts[category];
            if (recent.counts[category] >= 5 && recent.categoryDays[category] >= 3 &&
                recent.largest[category] <= amount * 0.35 && amount >= recent.total * 0.25) {
                add("weekly-small", IDS[category], focusMonth, 72,
                    "دفعات متفرّقة تجمّعت", "خلال ٧ أيام، سجّلت " + recent.counts[category] + " عمليات في «" +
                        label + "» مجموعها " + money(amount) + ".",
                    "شوف مجموع «" + label + "»", "توزّع " + money(amount) + " على " + recent.counts[category] +
                        " عمليات في آخر ٧ أيام. يمكنك مراجعتها معًا في السجل.");
            }
        }
        if (recent.days < 4 || previous.days < 4 || Math.abs(recent.days - previous.days) > 1) return;
        for (int category : FLEXIBLE) {
            long amount = recent.amounts[category];
            long prior = previous.amounts[category];
            if (previous.categoryDays[category] < 2 || recent.categoryDays[category] < 2 ||
                amount < prior * 1.60 || amount - prior < recent.total * 0.15) continue;
            String label = LABELS[category];
            int rise = percent(amount - prior, prior);
            add("weekly-category", IDS[category], focusMonth, 79,
                "«" + label + "» ارتفعت في آخر ٧ أيام", "مصروف آخر ٧ أيام على هذه الفئة " +
                    money(amount) + "، أعلى بـ " + rise + "٪ من الأيام السبعة السابقة.",
                "اتجاه صاعد في «" + label + "»", "انتقل مصروف الفئة من " + money(prior) +
                    " إلى " + money(amount) + " عند مقارنة فترتين كل منهما ٧ أيام.");
        }
        int change = percent(recent.total - previous.total, previous.total);
        if (change >= 35) {
            add("weekly-total", "", focusMonth, 74,
                "ارتفع صرف آخر ٧ أيام", "سجّلت " + money(recent.total) + "، أعلى بـ " + change +
                    "٪ من الأسبوع السابق. أكبر فئة خلال الفترة «" + LABELS[recent.leadingCategory()] + "».",
                "تغيّر في وتيرة صرفك", "إجمالي آخر ٧ أيام " + money(recent.total) +
                    " مقابل " + money(previous.total) + " في الأيام السبعة التي سبقتها.");
        } else if (change <= -25) {
            add("weekly-total", "", focusMonth, 68,
                "أسبوع أخفّ في المصروفات", "صرفك المسجّل في آخر ٧ أيام أقل بـ " + Math.abs(change) +
                    "٪ من الأسبوع السابق، مع تقارب عدد أيام التسجيل.",
                "انخفض الصرف المسجّل", "آخر ٧ أيام سجّلت " + money(recent.total) + " مقابل " +
                    money(previous.total) + " في الأيام السبعة السابقة.");
        }
    }

    private void add(String kind, String category, String month, int priority,
                     String title, String body, String alternativeTitle, String alternativeBody) {
        boolean alternate = (today + kind.hashCode() + category.hashCode()) % 2 != 0;
        candidates.add(new Insight(kind, category, month,
            alternate ? alternativeTitle : title, alternate ? alternativeBody : body, priority));
    }

    private Period between(int start, int end) {
        Period period = new Period();
        if (end >= start) {
            for (Day value : data.subMap(start, true, end, true).values()) {
                period.add(value);
            }
        }
        return period;
    }

    private long budget(String month) {
        return budgets == null ? 0 : budgets.optLong(month, 0);
    }

    private String money(long cents) {
        return "\u2066" + moneyFormat.format(cents / 100.0) + "\u2069 " + currency;
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) return 0;
        Collections.sort(values);
        int size = values.size();
        return (values.get(size / 2) + values.get((size - 1) / 2)) / 2;
    }

    private static int percent(long part, long whole) {
        if (whole <= 0) return 0;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(part * 100.0 / whole)));
    }

    private static String currencySymbol(String code) {
        switch (code) {
            case "USD": return "دولار";
            case "EUR": return "يورو";
            case "SAR": return "ر.س";
            case "AED": return "د.إ";
            case "TRY": return "₺";
            case "JOD": return "د.أ";
            case "EGP": return "ج.م";
            default: return "ل.س";
        }
    }

    static int localDay(Calendar date) {
        Calendar calendar = Calendar.getInstance(UTC, Locale.US);
        calendar.clear();
        calendar.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
        return (int) (calendar.getTimeInMillis() / DAY_MS);
    }

    static int civilDay(String iso) {
        return parseDay(dateFormat(), iso);
    }

    private static int parseDay(SimpleDateFormat parser, String iso) {
        if (iso.length() != 10) return Integer.MIN_VALUE;
        ParsePosition position = new ParsePosition(0);
        java.util.Date date = parser.parse(iso, position);
        if (date == null || position.getIndex() != 10) return Integer.MIN_VALUE;
        return (int) (date.getTime() / DAY_MS);
    }

    private static SimpleDateFormat dateFormat() {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        parser.setLenient(false);
        parser.setTimeZone(UTC);
        return parser;
    }

    static String iso(int day) {
        return dateFormat().format(new java.util.Date(day * DAY_MS));
    }

    private static int monthLength(int day) {
        Calendar calendar = Calendar.getInstance(UTC, Locale.US);
        calendar.setTimeInMillis(day * DAY_MS);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }
}
