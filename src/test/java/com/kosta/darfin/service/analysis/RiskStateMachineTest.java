package com.kosta.darfin.service.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RiskStateMachineTest {

    /** currentRatio만 채운 합성 분기 지표 — liquidity 카테고리 전이 검증용. */
    private static MetricsCalculator.QuarterMetrics quarter(String label, Double currentRatio) {
        MetricsCalculator.QuarterMetrics q = new MetricsCalculator.QuarterMetrics(label, "R" + label, false);
        q.metrics.put("currentRatio", currentRatio);
        q.metrics.put("zscores12q", Map.of());
        return q;
    }

    private static List<MetricsCalculator.QuarterMetrics> seriesOf(double... currentRatios) {
        List<MetricsCalculator.QuarterMetrics> out = new ArrayList<>();
        int year = 2023, qtr = 1;
        for (double cr : currentRatios) {
            out.add(quarter(year + "Q" + qtr, cr));
            if (++qtr > 4) {
                qtr = 1;
                year++;
            }
        }
        return out;
    }

    private static List<String> liquidityStates(List<RiskStateMachine.StateRow> rows) {
        return rows.stream()
                .filter(r -> r.category.equals("liquidity"))
                .map(r -> r.state)
                .collect(Collectors.toList());
    }

    @Test
    void insufficientHistoryYieldsDataInsufficientEverywhere() {
        List<RiskStateMachine.StateRow> rows = RiskStateMachine.run(seriesOf(1.5, 1.5, 1.5, 1.5)); // 4Q < 8Q
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> r.state.equals(RiskStateMachine.INSUFFICIENT));
    }

    @Test
    void healthySeriesStaysNormal() {
        List<RiskStateMachine.StateRow> rows =
                RiskStateMachine.run(seriesOf(1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5));
        assertThat(liquidityStates(rows))
                .containsExactly("정상", "정상", "정상", "정상", "정상", "정상", "정상", "정상");
    }

    @Test
    void flagTransitionsNewThenWorseningThenPersistingThenResolved() {
        // 정상×4 → 0.9(신규발생) → 0.7(악화: severity 역수 +29%) → 0.7(지속) → 1.5(해소)
        List<RiskStateMachine.StateRow> rows =
                RiskStateMachine.run(seriesOf(1.5, 1.5, 1.5, 1.5, 0.9, 0.7, 0.7, 1.5));
        assertThat(liquidityStates(rows))
                .containsExactly("정상", "정상", "정상", "정상", "신규발생", "악화", "지속", "해소");
    }

    @Test
    void improvingWhenSeverityDropsButStillFlagged() {
        // 0.6 → 0.9: 여전히 <1.0이지만 severity(1/CR)가 1.67→1.11로 −33% → 개선
        List<RiskStateMachine.StateRow> rows =
                RiskStateMachine.run(seriesOf(1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 0.6, 0.9));
        assertThat(liquidityStates(rows))
                .containsExactly("정상", "정상", "정상", "정상", "정상", "정상", "신규발생", "개선");
    }

    @Test
    void consecutiveQuartersCountStreakOfSameState() {
        List<RiskStateMachine.StateRow> rows =
                RiskStateMachine.run(seriesOf(1.5, 1.5, 1.5, 1.5, 0.9, 0.9, 0.9, 0.9));
        List<RiskStateMachine.StateRow> liquidity = rows.stream()
                .filter(r -> r.category.equals("liquidity")).collect(Collectors.toList());
        // 신규발생(1) → 지속(1) → 지속(2) → 지속(3): "지속 3분기 연속"
        RiskStateMachine.StateRow last = liquidity.get(liquidity.size() - 1);
        assertThat(last.state).isEqualTo(RiskStateMachine.PERSISTING);
        assertThat(last.consecutiveQtrs).isEqualTo(3);
    }

    @Test
    void governanceIsInsufficientInQuantOnlyLayer() {
        List<RiskStateMachine.StateRow> rows =
                RiskStateMachine.run(seriesOf(1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5));
        assertThat(rows.stream().filter(r -> r.category.equals("governance")))
                .allMatch(r -> r.state.equals(RiskStateMachine.INSUFFICIENT));
    }

    @Test
    void missingMetricForCategoryIsInsufficientNotNormal() {
        // currentRatio가 없는 분기 — 유동성 판정 불가는 정상이 아니라 데이터부족.
        List<MetricsCalculator.QuarterMetrics> series = seriesOf(1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5);
        series.get(7).metrics.put("currentRatio", null);
        List<String> states = liquidityStates(RiskStateMachine.run(series));
        assertThat(states.get(7)).isEqualTo(RiskStateMachine.INSUFFICIENT);
    }

    @Test
    void negativeEquityFlagsGoingConcern() {
        List<MetricsCalculator.QuarterMetrics> series = seriesOf(1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5);
        for (MetricsCalculator.QuarterMetrics q : series) {
            q.metrics.put("totalEquity", 100d);
        }
        series.get(7).metrics.put("totalEquity", -50d); // 자본잠식
        List<String> states = RiskStateMachine.run(series).stream()
                .filter(r -> r.category.equals("going_concern"))
                .map(r -> r.state)
                .collect(Collectors.toList());
        assertThat(states.get(6)).isEqualTo(RiskStateMachine.NORMAL);
        assertThat(states.get(7)).isEqualTo(RiskStateMachine.NEW);
    }

    @Test
    void signalsSnapshotIsRecordedForAudit() {
        List<RiskStateMachine.StateRow> rows =
                RiskStateMachine.run(seriesOf(1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 0.8));
        RiskStateMachine.StateRow last = rows.stream()
                .filter(r -> r.category.equals("liquidity") && r.quarter.equals("2024Q4"))
                .findFirst().orElseThrow();
        assertThat(last.signals.get("currentRatio")).isEqualTo(0.8d);
    }
}
