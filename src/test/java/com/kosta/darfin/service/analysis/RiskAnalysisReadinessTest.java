package com.kosta.darfin.service.analysis;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAnalysisReadinessTest {

    private static final String LATEST = "2026Q1";
    private static final Timestamp COMPUTED = Timestamp.from(Instant.parse("2026-07-15T01:00:00Z"));
    private static final Timestamp UPDATED = Timestamp.from(Instant.parse("2026-07-15T01:01:00Z"));

    @Test
    void requiresEveryExpectedCategoryForLatestQuarter() {
        List<RiskAnalysisDao.RiskStateRow> rows = freshLatestRows();
        rows.remove(rows.size() - 1);

        assertThat(RiskAnalysisService.latestNarrativesReady(rows, LATEST)).isFalse();
    }

    @Test
    void ignoresCompleteNarrativesFromOlderQuarter() {
        List<RiskAnalysisDao.RiskStateRow> rows = freshLatestRows("2025Q4");

        assertThat(RiskAnalysisService.latestNarrativesReady(rows, LATEST)).isFalse();
    }

    @Test
    void rejectsBlankOrStaleNarrativeFields() {
        List<RiskAnalysisDao.RiskStateRow> blank = freshLatestRows();
        blank.set(0, row(LATEST, RiskStateMachine.CATEGORIES.get(0), " ", "watch", COMPUTED, UPDATED));
        assertThat(RiskAnalysisService.latestNarrativesReady(blank, LATEST)).isFalse();

        List<RiskAnalysisDao.RiskStateRow> stale = freshLatestRows();
        Timestamp older = Timestamp.from(Instant.parse("2026-07-15T00:59:00Z"));
        stale.set(0, row(LATEST, RiskStateMachine.CATEGORIES.get(0), "narrative", "watch", COMPUTED, older));
        assertThat(RiskAnalysisService.latestNarrativesReady(stale, LATEST)).isFalse();
    }

    @Test
    void acceptsAllFreshLatestCategoryNarratives() {
        assertThat(RiskAnalysisService.latestNarrativesReady(freshLatestRows(), LATEST)).isTrue();
    }

    @Test
    void normalStateDoesNotRequireWatchNextText() {
        RiskAnalysisDao.RiskStateRow normal = row(
                LATEST, RiskStateMachine.CATEGORIES.get(0), "narrative", null, COMPUTED, UPDATED);

        assertThat(normal.isNarrativeFresh()).isTrue();
    }

    @Test
    void resolvedStateDoesNotRequireWatchNextText() {
        RiskAnalysisDao.RiskStateRow resolved = new RiskAnalysisDao.RiskStateRow(
                LATEST, RiskStateMachine.CATEGORIES.get(0), RiskStateMachine.RESOLVED, 0,
                null, null, "risk has resolved", null, COMPUTED, UPDATED);

        assertThat(resolved.isNarrativeFresh()).isTrue();
    }

    @Test
    void insufficientStateDoesNotRequireWatchNextText() {
        RiskAnalysisDao.RiskStateRow insufficient = new RiskAnalysisDao.RiskStateRow(
                LATEST, RiskStateMachine.CATEGORIES.get(0), RiskStateMachine.INSUFFICIENT, 0,
                null, null, "not enough governance data", null, COMPUTED, UPDATED);

        assertThat(insufficient.isNarrativeFresh()).isTrue();
    }

    @Test
    void mapsJobLifecycleWithoutLeakingRawErrors() {
        assertThat(RiskAnalysisService.responseStatus(false, false,
                new RiskAnalysisDao.RiskJobStatus("running", null, false)))
                .isEqualTo("generating_narrative");
        assertThat(RiskAnalysisService.responseStatus(false, false,
                new RiskAnalysisDao.RiskJobStatus("failed", "AI_TIMEOUT", true)))
                .isEqualTo("failed");
        assertThat(RiskAnalysisService.responseStatus(false, true,
                new RiskAnalysisDao.RiskJobStatus("failed", "AI_TIMEOUT", true)))
                .isEqualTo("complete");
    }

    private List<RiskAnalysisDao.RiskStateRow> freshLatestRows() {
        return freshLatestRows(LATEST);
    }

    private List<RiskAnalysisDao.RiskStateRow> freshLatestRows(String quarter) {
        List<RiskAnalysisDao.RiskStateRow> rows = new ArrayList<>();
        for (String category : RiskStateMachine.CATEGORIES) {
            rows.add(row(quarter, category, "narrative", "watch", COMPUTED, UPDATED));
        }
        return rows;
    }

    private RiskAnalysisDao.RiskStateRow row(String quarter, String category,
                                             String narrative, String watch,
                                             Timestamp computed, Timestamp updated) {
        return new RiskAnalysisDao.RiskStateRow(
                quarter, category, RiskStateMachine.NORMAL, 1,
                null, null, narrative, watch, computed, updated);
    }
}
