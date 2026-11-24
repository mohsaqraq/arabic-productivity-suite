package app.almahfaza.wallet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;

import static org.junit.Assert.*;

public final class SpendingInsightsTest {
    private final int today = SpendingInsights.civilDay("2026-11-18");
    private final JSONArray entries = new JSONArray();
    private final JSONObject budgets = new JSONObject();

    @Test
    public void categorySpikeComparesOnlyRecordedCategoryDays() throws JSONException {
        for (int i = 2; i <= 8; i += 2) expense(i, "food", 1000);
        expense(3, "study", 800);
        expense(1, "food", 2450);
        expense(0, "food", 20_000);
        SpendingInsights.Insight item = find("daily-category");
        assertNotNull(item);
        assertEquals("food", item.category);
        assertTrue(item.body.contains("24.5"));
        assertTrue(item.body.contains("10"));
        assertFalse(item.body.contains("200"));
    }

    @Test
    public void tooLittleHistoryProducesNoSpendingJudgment() throws JSONException {
        for (int i = 2; i <= 4; i++) expense(i, "food", 1000);
        expense(1, "food", 6400);
        assertTrue(review(today).isEmpty());
    }

    @Test
    public void largerHealthExpenseIsOnlyIncludedInOverallSpending() throws JSONException {
        for (int i = 2; i <= 8; i++) expense(i, "health", 1000);
        expense(1, "health", 4200);
        assertNotNull(find("daily-total"));
        assertNull(find("daily-category"));
        assertNull(find("weekly-category"));
    }

    @Test
    public void categoryRiseIsReportedAcrossComparableWeeks() throws JSONException {
        for (int i = 1; i <= 13; i++) expense(i, "food", i <= 7 ? 2250 : 1000);
        assertNotNull(find("weekly-category"));
        assertNotNull(find("weekly-total"));
    }

    @Test
    public void fallingSpendIsOnlyReportedWhenRecordingIsComparable() throws JSONException {
        for (int i = 1; i <= 7; i++) expense(i, "transport", 1000);
        for (int i = 8; i <= 14; i++) expense(i, "transport", 2000);
        String weeklyTitle = find("weekly-total").title;
        assertTrue(weeklyTitle.contains("أخفّ") || weeklyTitle.contains("انخفض"));
    }

    @Test
    public void sparseRecentWeekIsNotJudgedAsImproved() throws JSONException {
        for (int i = 8; i <= 14; i++) expense(i, "transport", 2000);
        expense(2, "transport", 1000);
        assertNull(find("weekly-total"));
    }

    @Test
    public void severalSmallPurchasesAreCombinedIntoOneObservation() throws JSONException {
        for (int i = 1; i <= 5; i++) expense(i, "play", 900);
        SpendingInsights.Insight item = find("weekly-small");
        assertNotNull(item);
        assertTrue(item.body.contains("45"));
        assertTrue(item.body.contains("5"));
    }

    @Test
    public void budgetCrossingGetsTheHighestPriority() throws JSONException {
        budgets.put("2026-11", 100_000);
        for (int i = 2; i <= 8; i++) expense(i, "food", 10_000);
        expense(1, "food", 41_000);
        assertEquals("budget-over", review(today).get(0).kind);
        assertNull(find("budget-near"));
    }

    @Test
    public void approachingTheLimitLeavesAUsefulAmount() throws JSONException {
        budgets.put("2026-11", 100_000);
        expense(3, "health", 55_000);
        expense(2, "market", 26_000);
        SpendingInsights.Insight item = find("budget-near");
        assertNotNull(item);
        assertTrue(item.body.contains("19"));
        assertTrue(item.oncePerMonth());
    }

    @Test
    public void budgetNearThresholdDoesNotRoundUpFromBelowEightyPercent() throws JSONException {
        budgets.put("2026-11", 100_000);
        expense(1, "market", 79_900);
        assertNull(find("budget-near"));
    }

    @Test
    public void monthBudgetPaceUsesElapsedDaysAndDataCoverage() throws JSONException {
        budgets.put("2026-11", 100_000);
        for (int i = 1; i <= 7; i++) expense(i, "market", 11_300);
        assertNotNull(find("budget-pace"));
    }

    @Test
    public void leapMonthSummaryReferencesTheCorrectMonth() throws JSONException {
        int day = SpendingInsights.civilDay("2028-03-02");
        budgets.put("2028-02", 100_000);
        for (int i = 26; i <= 29; i++) expenseOn("2028-02-" + i, "market", 10_500);
        SpendingInsights.Insight item = review(day).stream().filter(result -> result.kind.equals("month-close")).findFirst().get();
        assertEquals("2028-02", item.month);
        assertTrue(item.body.contains("420"));
        assertTrue(SpendingInsights.evaluate("{\"version\":1,\"records\":[]}", day).isEmpty());
    }

    private void expense(int daysAgo, String cat, long amount) throws JSONException {
        expenseOn(SpendingInsights.iso(today - daysAgo), cat, amount);
    }

    private void expenseOn(String date, String cat, long amount) throws JSONException {
        entries.put(new JSONObject().put("date", date).put("cat", cat).put("cents", amount));
    }

    private List<SpendingInsights.Insight> review(int day) throws JSONException {
        String source = new JSONObject().put("version", 1).put("records", entries)
            .put("budgets", budgets).put("currency", "USD").toString();
        return SpendingInsights.evaluate(source, day);
    }

    private SpendingInsights.Insight find(String kind) throws JSONException {
        return review(today).stream().filter(item -> item.kind.equals(kind)).findFirst().orElse(null);
    }
}
