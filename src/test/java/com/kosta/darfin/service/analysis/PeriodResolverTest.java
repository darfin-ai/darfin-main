package com.kosta.darfin.service.analysis;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodResolverTest {

    private final PeriodResolver resolver = new PeriodResolver(new ReportClassifier());

    private static Map<String, Object> item(String reportNm, String rceptNo, String rceptDt) {
        Map<String, Object> map = new HashMap<>();
        map.put("report_nm", reportNm);
        map.put("rcept_no", rceptNo);
        map.put("rcept_dt", rceptDt);
        return map;
    }

    @Test
    void correctionFilingKeepsOnlyLatestRceptDt() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("사업보고서 (2025.12)", "202601010001", "20260101"));
        items.add(item("[기재정정]사업보고서 (2025.12)", "202601150002", "20260115"));

        List<PeriodResolver.PeriodCandidate> candidates = resolver.periodicCandidatesFromList(items);

        assertThat(candidates).hasSize(1);
        PeriodResolver.PeriodCandidate candidate = candidates.get(0);
        assertThat(candidate.getRceptNo()).isEqualTo("202601150002");
        assertThat(candidate.getRceptDt()).isEqualTo("20260115");
        assertThat(candidate.getBsnsYear()).isEqualTo("2025");
        assertThat(candidate.getReprtCode()).isEqualTo("11011");
    }

    @Test
    void correctionFilingOutOfOrderStillKeepsLatestRceptDt() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("[기재정정]사업보고서 (2025.12)", "202601150002", "20260115"));
        items.add(item("사업보고서 (2025.12)", "202601010001", "20260101"));

        List<PeriodResolver.PeriodCandidate> candidates = resolver.periodicCandidatesFromList(items);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getRceptDt()).isEqualTo("20260115");
        assertThat(candidates.get(0).getRceptNo()).isEqualTo("202601150002");
    }

    @Test
    void emptyListReturnsNoCandidatesAndNullLatest() {
        List<Map<String, Object>> items = new ArrayList<>();

        assertThat(resolver.periodicCandidatesFromList(items)).isEmpty();
        assertThat(resolver.latestPeriodicFromList(items)).isNull();
    }

    @Test
    void allUnclassifiableItemsReturnNoCandidatesAndNullLatest() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("주요사항보고서 (2025.06)", "202601010001", "20260101"));
        items.add(item("단일판매공급계약체결", "202601020002", "20260102"));

        assertThat(resolver.periodicCandidatesFromList(items)).isEmpty();
        assertThat(resolver.latestPeriodicFromList(items)).isNull();
    }

    @Test
    void latestPeriodicFromListReturnsMostRecentAcrossPeriods() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("사업보고서 (2024.12)", "202503010001", "20250301"));
        items.add(item("분기보고서 (2025.03)", "202505150002", "20250515"));
        items.add(item("반기보고서 (2025.06)", "202508140003", "20250814"));

        PeriodResolver.PeriodCandidate latest = resolver.latestPeriodicFromList(items);

        assertThat(latest).isNotNull();
        assertThat(latest.getRceptDt()).isEqualTo("20250814");
        assertThat(latest.getReprtCode()).isEqualTo("11012");
        assertThat(latest.getBsnsYear()).isEqualTo("2025");
    }

    @Test
    void listFilingsDateRangeGoesBack548Days() {
        LocalDate today = LocalDate.of(2026, 7, 10);

        PeriodResolver.DateRange range = resolver.listFilingsDateRange(today);

        assertThat(range.getEndDe()).isEqualTo("20260710");
        assertThat(range.getBgnDe()).isEqualTo(today.minusDays(548).format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")));
    }
}
