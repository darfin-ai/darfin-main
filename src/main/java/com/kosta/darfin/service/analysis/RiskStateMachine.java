package com.kosta.darfin.service.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI분석의 리스크 상태머신 — 순수 함수. MetricsCalculator의 분기 지표 시계열을
 * 받아 카테고리별 분기 상태(신규발생/악화/지속/개선/해소/정상/데이터부족)와
 * 연속 분기 수를 산출한다. Layer 1(quant) 전용 — governance처럼 정량 신호가
 * 없는 카테고리는 데이터부족으로 두고 Layer 2(LLM 텍스트 추출)가 승격한다.
 *
 * 임계값은 팀 결정 전 placeholder — 전부 {@link Thresholds}에 모아 두었고,
 * 판정에 쓰인 지표 스냅샷을 quant_signals_json으로 남겨 감사 가능하게 한다.
 */
public final class RiskStateMachine {

    private RiskStateMachine() {
    }

    public static final List<String> CATEGORIES = List.of(
            "liquidity", "leverage", "earnings_quality", "going_concern", "governance", "operational");

    // 상태 라벨 — risk_states.state 컬럼 값과 1:1.
    public static final String NEW = "신규발생";
    public static final String WORSENING = "악화";
    public static final String PERSISTING = "지속";
    public static final String IMPROVING = "개선";
    public static final String RESOLVED = "해소";
    public static final String NORMAL = "정상";
    public static final String INSUFFICIENT = "데이터부족";

    /** 8분기 미만 이력이면 신뢰도 낮은 점수 대신 명시적 데이터부족. */
    static final int MIN_QUARTERS = 8;

    /**
     * 임계값 placeholder — 팀 논의 대상 (plan §8 open decision). 바꿀 때 여기만
     * 수정하면 된다. z 기준은 자기 12Q 이동창 z-score.
     */
    static final class Thresholds {
        static final double CURRENT_RATIO_FLAG = 1.0;      // 유동비율 < 1.0
        static final double DEBT_RATIO_FLAG = 2.0;         // 부채비율 > 200%
        static final double INTEREST_COVERAGE_FLAG = 1.0;  // 이자보상배율 < 1
        static final double ACCRUALS_FLAG = 0.10;          // (순이익−CFO)/총자산 > 10%
        static final double ALTMAN_Z_DISTRESS = 1.1;       // Z' 부실 구간
        static final double ZSCORE_FLAG = -1.5;            // 자기 기준선 대비 −1.5σ
        static final double SEVERITY_BAND = 0.10;          // 악화/개선 판정용 ±10% 밴드

        private Thresholds() {
        }
    }

    /** 한 분기·한 카테고리의 판정 결과. */
    public static class StateRow {
        public final String quarter;
        public final String category;
        public final String state;
        public final int consecutiveQtrs;
        /** 판정에 쓰인 신호 스냅샷 (risk_states.quant_signals_json). */
        public final Map<String, Object> signals;

        StateRow(String quarter, String category, String state, int consecutiveQtrs, Map<String, Object> signals) {
            this.quarter = quarter;
            this.category = category;
            this.state = state;
            this.consecutiveQtrs = consecutiveQtrs;
            this.signals = signals;
        }
    }

    /** 분기 지표 시계열(오래된 것부터) → 전 분기 × 전 카테고리 상태 행. */
    public static List<StateRow> run(List<MetricsCalculator.QuarterMetrics> series) {
        List<StateRow> out = new ArrayList<>();
        boolean insufficientHistory = series.size() < MIN_QUARTERS;

        for (String category : CATEGORIES) {
            String prevState = null;
            Double prevSeverity = null;
            int streak = 0;

            for (MetricsCalculator.QuarterMetrics q : series) {
                Assessment a = insufficientHistory
                        ? Assessment.insufficient("history<" + MIN_QUARTERS + "Q")
                        : assess(category, q);

                String state = transition(a, prevState, prevSeverity);
                streak = state.equals(prevState) ? streak + 1 : 1;
                out.add(new StateRow(q.quarter, category, state, streak, a.signals));

                prevState = state;
                prevSeverity = a.severity;
            }
        }
        return out;
    }

    /** flag(문제 있음) + severity(높을수록 나쁨, 악화/개선 비교용) + 판정 근거. */
    private static class Assessment {
        final Boolean flagged;   // null = 판정 불가(데이터부족)
        final Double severity;
        final Map<String, Object> signals;

        Assessment(Boolean flagged, Double severity, Map<String, Object> signals) {
            this.flagged = flagged;
            this.severity = severity;
            this.signals = signals;
        }

        static Assessment insufficient(String reason) {
            Map<String, Object> signals = new LinkedHashMap<>();
            signals.put("insufficientReason", reason);
            return new Assessment(null, null, signals);
        }
    }

    private static String transition(Assessment a, String prevState, Double prevSeverity) {
        if (a.flagged == null) {
            return INSUFFICIENT;
        }
        boolean prevFlagged = prevState != null
                && !NORMAL.equals(prevState) && !RESOLVED.equals(prevState) && !INSUFFICIENT.equals(prevState);
        if (!a.flagged) {
            return prevFlagged ? RESOLVED : NORMAL;
        }
        if (!prevFlagged) {
            return NEW;
        }
        if (a.severity != null && prevSeverity != null && prevSeverity != 0d) {
            double change = (a.severity - prevSeverity) / Math.abs(prevSeverity);
            if (change > Thresholds.SEVERITY_BAND) return WORSENING;
            if (change < -Thresholds.SEVERITY_BAND) return IMPROVING;
        }
        return PERSISTING;
    }

    @SuppressWarnings("unchecked")
    private static Double z(MetricsCalculator.QuarterMetrics q, String metric) {
        Object zscores = q.metrics.get("zscores12q");
        if (!(zscores instanceof Map)) return null;
        Object v = ((Map<String, Object>) zscores).get(metric);
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    private static Assessment assess(String category, MetricsCalculator.QuarterMetrics q) {
        Map<String, Object> signals = new LinkedHashMap<>();
        switch (category) {
            case "liquidity": {
                Double currentRatio = q.get("currentRatio");
                Double zCr = z(q, "currentRatio");
                signals.put("currentRatio", currentRatio);
                signals.put("currentRatioZ", zCr);
                signals.put("shortTermBorrowings", q.get("shortTermBorrowings"));
                if (currentRatio == null) return new Assessment(null, null, signals);
                boolean flagged = currentRatio < Thresholds.CURRENT_RATIO_FLAG
                        || (zCr != null && zCr < Thresholds.ZSCORE_FLAG);
                // severity: 유동비율 역수 — 낮을수록(나쁠수록) 커진다.
                return new Assessment(flagged, currentRatio == 0d ? null : 1d / currentRatio, signals);
            }
            case "leverage": {
                Double debtRatio = q.get("debtRatio");
                Double coverage = q.get("interestCoverage");
                signals.put("debtRatio", debtRatio);
                signals.put("interestCoverage", coverage);
                signals.put("debtRatioZ", z(q, "debtRatio"));
                if (debtRatio == null && coverage == null) return new Assessment(null, null, signals);
                boolean flagged = (debtRatio != null &&
                        (debtRatio > Thresholds.DEBT_RATIO_FLAG || debtRatio < 0)) // 음수 = 자본잠식
                        || (coverage != null && coverage < Thresholds.INTEREST_COVERAGE_FLAG);
                Double severity = debtRatio == null ? null : (debtRatio < 0 ? Double.MAX_VALUE : debtRatio);
                return new Assessment(flagged, severity, signals);
            }
            case "earnings_quality": {
                Double accruals = q.get("accrualsRatio");
                Double ni = q.get("netIncome");
                Double cfo = q.get("cfo");
                signals.put("accrualsRatio", accruals);
                signals.put("netIncome", ni);
                signals.put("cfo", cfo);
                signals.put("piotroskiF", q.metrics.get("piotroskiF"));
                if (accruals == null) return new Assessment(null, null, signals);
                boolean flagged = accruals > Thresholds.ACCRUALS_FLAG
                        || (ni != null && cfo != null && ni > 0 && cfo < 0);
                return new Assessment(flagged, accruals, signals);
            }
            case "going_concern": {
                Double equity = q.get("totalEquity");
                Double altmanZ = q.get("altmanZ");
                signals.put("totalEquity", equity);
                signals.put("altmanZ", altmanZ);
                if (equity == null && altmanZ == null) return new Assessment(null, null, signals);
                boolean flagged = (equity != null && equity < 0)
                        || (altmanZ != null && altmanZ < Thresholds.ALTMAN_Z_DISTRESS);
                // severity: Z' 반전(낮을수록 나쁨). Z' 없으면 자본잠식 여부만으로 상수.
                Double severity = altmanZ != null ? -altmanZ : (equity != null && equity < 0 ? 1d : 0d);
                return new Assessment(flagged, severity, signals);
            }
            case "governance":
                // 정량 신호 없음 — Layer 2(LLM: 최대주주/대표이사 변경, 특수관계자
                // 거래 등 text_extractions)가 채우기 전까지 명시적 데이터부족.
                return Assessment.insufficient("quant_layer_has_no_governance_signal");
            case "operational": {
                Double zTurnover = z(q, "assetTurnoverTtm");
                Double zMargin = z(q, "operatingMargin");
                Double zCcc = z(q, "ccc");
                signals.put("assetTurnoverZ", zTurnover);
                signals.put("operatingMarginZ", zMargin);
                signals.put("cccZ", zCcc);
                signals.put("operatingMargin", q.get("operatingMargin"));
                if (zTurnover == null && zMargin == null && zCcc == null) {
                    return new Assessment(null, null, signals);
                }
                boolean flagged = (zTurnover != null && zTurnover < Thresholds.ZSCORE_FLAG)
                        || (zMargin != null && zMargin < Thresholds.ZSCORE_FLAG)
                        || (zCcc != null && zCcc > -Thresholds.ZSCORE_FLAG); // CCC는 증가가 악화
                double severity = 0d;
                if (zTurnover != null) severity += -zTurnover;
                if (zMargin != null) severity += -zMargin;
                if (zCcc != null) severity += zCcc;
                return new Assessment(flagged, severity, signals);
            }
            default:
                return Assessment.insufficient("unknown_category");
        }
    }
}
