package com.kosta.darfin.service.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsCalculatorTest {

    // ── fixture helpers ──────────────────────────────────────────────────

    private static Map<String, Object> row(String year, String reprtCode, String statementType,
                                           String accountNm, String qualifier, long amount) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rcept_no", "R" + year + reprtCode);
        r.put("corp_code", "00000001");
        r.put("bsns_year", year);
        r.put("reprt_code", reprtCode);
        r.put("concept", null);
        r.put("account_nm", accountNm);
        r.put("statement_type", statementType);
        r.put("ord", null);
        r.put("is_consolidated", true);
        r.put("period_qualifier", qualifier);
        r.put("amount", amount);
        return r;
    }

    /** 한 분기 표준 세트: 손익 3개월/누적 + 재무상태표 + 현금흐름 누적. */
    private static List<Map<String, Object>> quarterRows(
            String year, String reprtCode, long revenue, long revenueCumulative,
            long assets, long liabilities, long currentAssets, long currentLiabilities, long cfoCumulative) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(year, reprtCode, "손익계산서", "매출액", "3개월", revenue));
        rows.add(row(year, reprtCode, "손익계산서", "매출액", "누적", revenueCumulative));
        rows.add(row(year, reprtCode, "손익계산서", "영업이익", "3개월", revenue / 10));
        rows.add(row(year, reprtCode, "손익계산서", "영업이익", "누적", revenueCumulative / 10));
        rows.add(row(year, reprtCode, "손익계산서", "당기순이익", "3개월", revenue / 20));
        rows.add(row(year, reprtCode, "손익계산서", "당기순이익", "누적", revenueCumulative / 20));
        rows.add(row(year, reprtCode, "재무상태표", "자산총계", null, assets));
        rows.add(row(year, reprtCode, "재무상태표", "부채총계", null, liabilities));
        rows.add(row(year, reprtCode, "재무상태표", "자본총계", null, assets - liabilities));
        rows.add(row(year, reprtCode, "재무상태표", "유동자산", null, currentAssets));
        rows.add(row(year, reprtCode, "재무상태표", "유동부채", null, currentLiabilities));
        rows.add(row(year, reprtCode, "현금흐름표", "영업활동현금흐름", null, cfoCumulative));
        return rows;
    }

    /** 사업보고서(11011): 손익은 연간 단일값(qualifier null), 재무상태표는 Q4 스냅샷. */
    private static List<Map<String, Object>> annualRows(
            String year, long revenueAnnual, long assets, long liabilities,
            long currentAssets, long currentLiabilities, long cfoAnnual) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(year, "11011", "손익계산서", "매출액", null, revenueAnnual));
        rows.add(row(year, "11011", "손익계산서", "영업이익", null, revenueAnnual / 10));
        rows.add(row(year, "11011", "손익계산서", "당기순이익", null, revenueAnnual / 20));
        rows.add(row(year, "11011", "재무상태표", "자산총계", null, assets));
        rows.add(row(year, "11011", "재무상태표", "부채총계", null, liabilities));
        rows.add(row(year, "11011", "재무상태표", "자본총계", null, assets - liabilities));
        rows.add(row(year, "11011", "재무상태표", "유동자산", null, currentAssets));
        rows.add(row(year, "11011", "재무상태표", "유동부채", null, currentLiabilities));
        rows.add(row(year, "11011", "현금흐름표", "영업활동현금흐름", null, cfoAnnual));
        return rows;
    }

    private static List<Map<String, Object>> fullYear(String year, long qRevenue) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(quarterRows(year, "11013", qRevenue, qRevenue, 1000, 400, 300, 200, 50));
        rows.addAll(quarterRows(year, "11012", qRevenue, qRevenue * 2, 1000, 400, 300, 200, 100));
        rows.addAll(quarterRows(year, "11014", qRevenue, qRevenue * 3, 1000, 400, 300, 200, 150));
        rows.addAll(annualRows(year, qRevenue * 4, 1000, 400, 300, 200, 200));
        return rows;
    }

    private static MetricsCalculator.QuarterMetrics find(List<MetricsCalculator.QuarterMetrics> series, String quarter) {
        return series.stream().filter(q -> q.quarter.equals(quarter)).findFirst().orElseThrow();
    }

    // ── tests ────────────────────────────────────────────────────────────

    @Test
    void derivesQ4FromAnnualMinusQ3Cumulative() {
        List<Map<String, Object>> rows = fullYear("2025", 100);
        List<MetricsCalculator.QuarterMetrics> series = MetricsCalculator.compute(rows, true);

        assertThat(series).extracting(q -> q.quarter)
                .containsExactly("2025Q1", "2025Q2", "2025Q3", "2025Q4");
        MetricsCalculator.QuarterMetrics q4 = find(series, "2025Q4");
        // Q4 매출 = 연간 400 − 3분기 누적 300 = 100
        assertThat(q4.get("revenue")).isEqualTo(100d);
        assertThat(q4.derivedQ4).isTrue();
        assertThat(q4.metrics.get("valueOrigin")).isEqualTo("derived_q4");
        // Q1~Q3는 보고된 3개월 값
        assertThat(find(series, "2025Q2").metrics.get("valueOrigin")).isEqualTo("reported");
    }

    @Test
    void annualWithoutQ3CumulativeSkipsIncomeStatementQ4() {
        // 분기보고서가 아예 없는 연도의 사업보고서 — 연간 총액을 분기값으로 쓰면 안 된다.
        List<Map<String, Object>> rows = annualRows("2025", 400, 1000, 400, 300, 200, 200);
        List<MetricsCalculator.QuarterMetrics> series = MetricsCalculator.compute(rows, true);

        MetricsCalculator.QuarterMetrics q4 = find(series, "2025Q4");
        assertThat(q4.get("revenue")).isNull();
        // 재무상태표 시점 수치는 그대로 살아있다.
        assertThat(q4.get("currentRatio")).isEqualTo(1.5d);
    }

    @Test
    void cfoIsDerivedByCumulativeDifference() {
        List<MetricsCalculator.QuarterMetrics> series = MetricsCalculator.compute(fullYear("2025", 100), true);
        assertThat(find(series, "2025Q1").get("cfo")).isEqualTo(50d);   // Q1 누적 그대로
        assertThat(find(series, "2025Q2").get("cfo")).isEqualTo(50d);   // 100 − 50
        assertThat(find(series, "2025Q4").get("cfo")).isEqualTo(50d);   // 200 − 150
    }

    @Test
    void computesBasicRatios() {
        List<MetricsCalculator.QuarterMetrics> series = MetricsCalculator.compute(fullYear("2025", 100), true);
        MetricsCalculator.QuarterMetrics q1 = find(series, "2025Q1");
        assertThat(q1.get("operatingMargin")).isCloseTo(0.10, within(1e-9));  // 10/100
        assertThat(q1.get("currentRatio")).isCloseTo(1.5, within(1e-9));      // 300/200
        assertThat(q1.get("debtRatio")).isCloseTo(400d / 600d, within(1e-9)); // 부채/자본
    }

    @Test
    void ttmMetricsRequireFourConsecutiveQuarters() {
        List<MetricsCalculator.QuarterMetrics> series = MetricsCalculator.compute(fullYear("2025", 100), true);
        assertThat(find(series, "2025Q3").get("roaTtm")).isNull();
        // Q4에서 TTM NI = 5×4 = 20, 자산 1000 → ROA 2%
        assertThat(find(series, "2025Q4").get("roaTtm")).isCloseTo(0.02, within(1e-9));
    }

    @Test
    void filtersByConsolidationFlag() {
        List<Map<String, Object>> rows = fullYear("2025", 100);
        rows.forEach(r -> r.put("is_consolidated", false));
        assertThat(MetricsCalculator.compute(rows, true)).isEmpty();
        assertThat(MetricsCalculator.compute(rows, false)).hasSize(4);
    }

    @Test
    void zscoresNullUntilEightQuartersThenComputed() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int y = 2023; y <= 2025; y++) {
            rows.addAll(fullYear(String.valueOf(y), 100));
        }
        List<MetricsCalculator.QuarterMetrics> series = MetricsCalculator.compute(rows, true);
        assertThat(series).hasSize(12);

        @SuppressWarnings("unchecked")
        Map<String, Object> earlyZ = (Map<String, Object>) find(series, "2023Q4").metrics.get("zscores12q");
        assertThat(earlyZ.get("currentRatio")).isNull(); // 4분기째 — 표본 8 미만

        @SuppressWarnings("unchecked")
        Map<String, Object> lateZ = (Map<String, Object>) find(series, "2025Q4").metrics.get("zscores12q");
        // 12분기 동일값 → 표준편차 0 → z=0 (null 아님)
        assertThat((Double) lateZ.get("currentRatio")).isEqualTo(0d);
    }

    @Test
    void normalizesAccountNameVariants() {
        // '수익(매출액)'과 '분기순이익'이 표준 개념으로 접힌다.
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("2025", "11013", "손익계산서", "수익(매출액)", "3개월", 100));
        rows.add(row("2025", "11013", "손익계산서", "분기순이익", "3개월", 5));
        rows.add(row("2025", "11013", "재무상태표", "자산총계", null, 1000));
        List<MetricsCalculator.QuarterMetrics> series = MetricsCalculator.compute(rows, true);
        MetricsCalculator.QuarterMetrics q1 = find(series, "2025Q1");
        assertThat(q1.get("revenue")).isEqualTo(100d);
        assertThat(q1.get("netIncome")).isEqualTo(5d);
    }
}
