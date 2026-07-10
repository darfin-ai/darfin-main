package com.kosta.darfin.service.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Ports the list.json-only parts of {@code dart_period.py}: {@code periodic_candidates_from_list},
 * {@code latest_periodic_from_list}, {@code list_filings_date_range}. Does not port
 * {@code resolve_latest_period_sync} — that is the batch-only sync variant.
 */
@Component
public class PeriodResolver {

    private static final int LIST_LOOKBACK_DAYS = 548;
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReportClassifier reportClassifier;

    public PeriodResolver(ReportClassifier reportClassifier) {
        this.reportClassifier = reportClassifier;
    }

    @Getter
    @RequiredArgsConstructor
    public static class PeriodCandidate {
        private final String rceptNo;
        private final String bsnsYear;
        private final String reprtCode;
        private final String rceptDt;
    }

    /**
     * Raw list.json items -> deduped-by-period candidates, most recent rcept_dt first.
     * Duplicate (bsnsYear, reprtCode) pairs (corrections/정정공시) keep only the latest rcept_dt.
     */
    public List<PeriodCandidate> periodicCandidatesFromList(List<Map<String, Object>> items) {
        Map<List<String>, PeriodCandidate> byPeriod = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String reportNm = String.valueOf(item.getOrDefault("report_nm", "")).trim();
            ReportClassifier.Classified classified = reportClassifier.classify(reportNm);
            if (classified == null) continue;
            String rceptDt = String.valueOf(item.getOrDefault("rcept_dt", ""));
            List<String> key = List.of(classified.getBsnsYear(), classified.getReprtCode());
            PeriodCandidate existing = byPeriod.get(key);
            if (existing != null && existing.getRceptDt().compareTo(rceptDt) >= 0) continue;
            byPeriod.put(key, new PeriodCandidate(
                    String.valueOf(item.get("rcept_no")), classified.getBsnsYear(), classified.getReprtCode(), rceptDt));
        }
        List<PeriodCandidate> out = new ArrayList<>(byPeriod.values());
        out.sort((a, b) -> b.getRceptDt().compareTo(a.getRceptDt())); // descending
        return out;
    }

    public PeriodCandidate latestPeriodicFromList(List<Map<String, Object>> items) {
        List<PeriodCandidate> candidates = periodicCandidatesFromList(items);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    @Getter
    @RequiredArgsConstructor
    public static class DateRange {
        private final String bgnDe;
        private final String endDe;
    }

    public DateRange listFilingsDateRange(LocalDate today) {
        LocalDate end = today != null ? today : LocalDate.now();
        LocalDate begin = end.minusDays(LIST_LOOKBACK_DAYS);
        return new DateRange(begin.format(YYYYMMDD), end.format(YYYYMMDD));
    }
}
