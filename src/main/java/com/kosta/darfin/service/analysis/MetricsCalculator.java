package com.kosta.darfin.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * AI분석 Layer 1의 결정론적 지표 계산 — 전부 순수 함수(네트워크/DB 없음).
 * 입력은 FinancialFactsService.liveMetricRows()가 주는 계정 행(financial_facts
 * payload를 FinancialFactTransformer로 변환한 shape)이고, 출력은 분기별 지표
 * 스냅샷이다. LLM은 여기 관여하지 않는다 — 비율/점수는 감사 가능해야 하므로
 * 공식으로만 계산한다.
 *
 * 분기 환산 규칙은 CompanyAnalysisService.buildQuarterSeries()와 동일한 한국
 * 공시 관행을 따른다:
 * - 재무상태표: 시점 수치 그대로 (사업보고서 행 = Q4 스냅샷).
 * - 손익계산서: '3개월' 행 우선, Q4 = 사업보고서 연간 − 3분기 누적
 *   (value_origin='derived_q4'로 표시 — Q4 보고서는 존재하지 않는다).
 * - 현금흐름표: 누적치만 공시되므로 분기값 = 당분기 누적 − 직전 분기 누적.
 * 연결(CFS)/별도(OFS)는 호출자가 is_consolidated로 필터해 넘긴다 — 한 시계열에
 * 절대 섞지 않는다.
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
    }

    /** 분기 정렬 순서 (reprt_code): Q1, Q2, Q3, Q4(사업보고서). */
    private static final List<String> QUARTER_ORDER = List.of("11013", "11012", "11014", "11011");
    private static final Map<String, String> QUARTER_SUFFIX = Map.of(
            "11013", "Q1", "11012", "Q2", "11014", "Q3", "11011", "Q4");

    /** 자기 기준선(z-score) 창 크기와 최소 표본 — 12Q 이동창, 8Q 미만이면 z 없음. */
    static final int ZSCORE_WINDOW = 12;
    static final int MIN_QUARTERS_FOR_ZSCORE = 8;

    /** 분기 지표 스냅샷. metrics는 derived_metrics.metrics_json으로 그대로 직렬화된다. */
    public static class QuarterMetrics {
        public final String quarter;          // 예: 2026Q1
        public final String rceptNo;          // 이 분기 값의 근거 공시 (staleness 판정)
        public final boolean derivedQ4;       // 손익 수치가 연간 − 3분기 누적 환산인지
        public final Map<String, Object> metrics = new LinkedHashMap<>();

        QuarterMetrics(String quarter, String rceptNo, boolean derivedQ4) {
            this.quarter = quarter;
            this.rceptNo = rceptNo;
            this.derivedQ4 = derivedQ4;
        }

        public Double get(String key) {
            Object v = metrics.get(key);
            return v instanceof Number ? ((Number) v).doubleValue() : null;
        }
    }

    /**
     * 계정 행 → 분기별 지표 시계열(오래된 분기부터). 계정명 매칭이 하나도 안 되는
     * 분기는 건너뛴다(negative cache/파싱 실패 분기와 동일 취급).
     */
    public static List<QuarterMetrics> compute(List<Map<String, Object>> accountRows, boolean isConsolidated) {
        Map<String, PeriodFacts> byPeriod = extractPeriodFacts(accountRows, isConsolidated);
        List<QuarterFin> series = buildQuarterlySeries(byPeriod);
        return computeMetrics(series);
    }

    // ── 1단계: 계정 행 → (연도, 보고서)별 canonical 개념 값 ────────────────

    /** 시계열 분절을 막기 위한 계정명 정규화 — CompanyAnalysisService.normalizeAccountNm과 동일 규칙. */
    private static String normalize(String accountNm) {
        String n = accountNm.replaceAll("\\s*\\(주\\d+\\)", "").replace(" ", "");
        n = n.replace("(손실)", "");
        n = n.replaceAll("^(분기|반기)순이익$", "당기순이익");
        if (n.equals("수익(매출액)")) n = "매출액";
        return n;
    }

    /** canonical 개념 → 정규화된 계정명 후보(우선순위 순). */
    private static final Map<String, List<String>> CONCEPT_ALIASES = new LinkedHashMap<>();

    static {
        // 재무상태표 (시점 수치)
        CONCEPT_ALIASES.put("totalAssets", List.of("자산총계"));
        CONCEPT_ALIASES.put("totalLiabilities", List.of("부채총계"));
        CONCEPT_ALIASES.put("totalEquity", List.of("자본총계"));
        CONCEPT_ALIASES.put("currentAssets", List.of("유동자산"));
        CONCEPT_ALIASES.put("currentLiabilities", List.of("유동부채"));
        CONCEPT_ALIASES.put("cash", List.of("현금및현금성자산"));
        CONCEPT_ALIASES.put("inventory", List.of("재고자산"));
        CONCEPT_ALIASES.put("receivables", List.of("매출채권", "매출채권및기타채권", "매출채권및기타유동채권"));
        CONCEPT_ALIASES.put("payables", List.of("매입채무", "매입채무및기타채무", "매입채무및기타유동채무"));
        CONCEPT_ALIASES.put("retainedEarnings", List.of("이익잉여금", "이익잉여금(결손금)"));
        CONCEPT_ALIASES.put("shortTermBorrowings", List.of("단기차입금"));
        // 손익계산서 (분기 흐름)
        CONCEPT_ALIASES.put("revenue", List.of("매출액", "영업수익"));
        CONCEPT_ALIASES.put("operatingIncome", List.of("영업이익", "영업이익(손실)"));
        CONCEPT_ALIASES.put("netIncome", List.of("당기순이익"));
        CONCEPT_ALIASES.put("interestExpense", List.of("이자비용", "금융비용"));
        // 현금흐름표 (연중 누적)
        CONCEPT_ALIASES.put("cfo", List.of("영업활동현금흐름", "영업활동으로인한현금흐름"));
    }

    private static final List<String> BS_CONCEPTS = List.of(
            "totalAssets", "totalLiabilities", "totalEquity", "currentAssets", "currentLiabilities",
            "cash", "inventory", "receivables", "payables", "retainedEarnings", "shortTermBorrowings");
    private static final List<String> IS_CONCEPTS = List.of(
            "revenue", "operatingIncome", "netIncome", "interestExpense");

    private static class PeriodFacts {
        String rceptNo;
        final Map<String, Long> point = new HashMap<>();      // 재무상태표
        final Map<String, Long> threeMonth = new HashMap<>(); // 손익 '3개월'
        final Map<String, Long> annual = new HashMap<>();     // 손익 사업보고서 연간 (qualifier null)
        final Map<String, Long> cumulative = new HashMap<>(); // 손익/현금흐름 '누적' + CF qualifier null
    }

    private static String periodKey(String year, String reprtCode) {
        return year + "|" + reprtCode;
    }

    private static Map<String, PeriodFacts> extractPeriodFacts(List<Map<String, Object>> rows, boolean isConsolidated) {
        // 정정공시 중복은 나중 행이 덮어쓴다 (입력이 적재 순서라는 전제 — financials()와 동일).
        Map<String, PeriodFacts> byPeriod = new TreeMap<>();
        for (Map<String, Object> r : rows) {
            if (r.get("amount") == null) continue;
            Boolean consolidated = (Boolean) r.get("is_consolidated");
            if (consolidated == null || consolidated != isConsolidated) continue;

            String statementType = (String) r.get("statement_type");
            String concept = matchConcept(statementType, normalize((String) r.get("account_nm")));
            if (concept == null) continue;

            String year = (String) r.get("bsns_year");
            String reprtCode = (String) r.get("reprt_code");
            PeriodFacts facts = byPeriod.computeIfAbsent(periodKey(year, reprtCode), k -> new PeriodFacts());
            facts.rceptNo = (String) r.get("rcept_no");

            long amount = ((Number) r.get("amount")).longValue();
            String qualifier = (String) r.get("period_qualifier");
            if ("재무상태표".equals(statementType)) {
                if (qualifier == null) facts.point.put(concept, amount);
            } else if ("손익계산서".equals(statementType)) {
                if ("3개월".equals(qualifier)) facts.threeMonth.put(concept, amount);
                else if ("누적".equals(qualifier)) facts.cumulative.put(concept, amount);
                else if ("11011".equals(reprtCode)) facts.annual.put(concept, amount);
            } else if ("현금흐름표".equals(statementType)) {
                // 현금흐름표는 항상 연중 누적(qualifier null 또는 '누적').
                facts.cumulative.put(concept, amount);
            }
        }
        return byPeriod;
    }

    private static String matchConcept(String statementType, String normalizedNm) {
        for (Map.Entry<String, List<String>> e : CONCEPT_ALIASES.entrySet()) {
            String concept = e.getKey();
            boolean statementOk =
                    (BS_CONCEPTS.contains(concept) && "재무상태표".equals(statementType))
                            || (IS_CONCEPTS.contains(concept) && "손익계산서".equals(statementType))
                            || ("cfo".equals(concept) && "현금흐름표".equals(statementType));
            if (statementOk && e.getValue().contains(normalizedNm)) {
                return concept;
            }
        }
        return null;
    }

    // ── 2단계: 기간별 사실 → 순수 분기 흐름/시점 시계열 ─────────────────────

    static class QuarterFin {
        String quarter;
        String rceptNo;
        boolean derivedQ4;
        final Map<String, Long> values = new HashMap<>(); // canonical concept → 분기값/시점값

        Double v(String concept) {
            Long x = values.get(concept);
            return x == null ? null : x.doubleValue();
        }
    }

    private static List<QuarterFin> buildQuarterlySeries(Map<String, PeriodFacts> byPeriod) {
        // 연도별로 분기 순서대로 순회하며 손익 Q4 환산·현금흐름 누적 차감을 수행.
        Map<String, Map<String, PeriodFacts>> byYear = new TreeMap<>();
        for (Map.Entry<String, PeriodFacts> e : byPeriod.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            byYear.computeIfAbsent(parts[0], k -> new HashMap<>()).put(parts[1], e.getValue());
        }

        List<QuarterFin> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, PeriodFacts>> yearEntry : byYear.entrySet()) {
            String year = yearEntry.getKey();
            Map<String, PeriodFacts> byReprt = yearEntry.getValue();
            PeriodFacts q3 = byReprt.get("11014");
            Long prevCfoCumulative = null;
            boolean cfoChainBroken = false;

            for (String reprtCode : QUARTER_ORDER) {
                PeriodFacts facts = byReprt.get(reprtCode);
                if (facts == null) {
                    cfoChainBroken = true; // 중간 분기가 비면 이후 누적 차감 불가
                    continue;
                }
                QuarterFin q = new QuarterFin();
                q.quarter = year + QUARTER_SUFFIX.get(reprtCode);
                q.rceptNo = facts.rceptNo;

                // 재무상태표: 시점 수치 그대로.
                q.values.putAll(facts.point);

                // 손익: 분기 행 우선, Q4는 연간 − 3분기 누적.
                if ("11011".equals(reprtCode)) {
                    for (String concept : IS_CONCEPTS) {
                        Long annual = facts.annual.get(concept);
                        Long q3Cum = q3 != null ? q3.cumulative.get(concept) : null;
                        if (annual != null && q3Cum != null) {
                            q.values.put(concept, annual - q3Cum);
                            q.derivedQ4 = true;
                        }
                        // 3분기 누적이 없으면 연간 총액을 분기값으로 쓰지 않는다(4배 스파이크 방지).
                    }
                } else {
                    for (String concept : IS_CONCEPTS) {
                        Long v = facts.threeMonth.get(concept);
                        if (v != null) q.values.put(concept, v);
                    }
                }

                // 현금흐름: 누적 차감 (Q1은 누적 그대로).
                Long cfoCum = facts.cumulative.get("cfo");
                if (cfoCum != null) {
                    if ("11013".equals(reprtCode)) {
                        q.values.put("cfo", cfoCum);
                    } else if (prevCfoCumulative != null && !cfoChainBroken) {
                        q.values.put("cfo", cfoCum - prevCfoCumulative);
                    }
                    prevCfoCumulative = cfoCum;
                    cfoChainBroken = false;
                } else {
                    cfoChainBroken = true;
                }

                if (!q.values.isEmpty()) {
                    out.add(q);
                }
            }
        }
        out.sort(Comparator.comparing(q -> q.quarter));
        return out;
    }

    // ── 3단계: 분기 시계열 → 비율/점수/자기 z-score ─────────────────────────

    private static Double div(Double a, Double b) {
        return (a == null || b == null || b == 0d) ? null : a / b;
    }

    /** "2026Q1" → 통산 분기 인덱스 (연도×4 + 분기). 결측 분기 검출용. */
    private static int quarterIndex(String quarter) {
        return Integer.parseInt(quarter.substring(0, 4)) * 4
                + Integer.parseInt(quarter.substring(5)) - 1;
    }

    private static Double ttm(List<QuarterFin> series, int idx, String concept) {
        // 직전 4개 분기(현재 포함)가 달력상 연속이고 값이 모두 있어야 TTM.
        // 아니면 null — 결측 분기를 건너뛰거나 0으로 치면 흐름 지표가 조용히 왜곡된다.
        if (idx < 3) return null;
        if (quarterIndex(series.get(idx).quarter) - quarterIndex(series.get(idx - 3).quarter) != 3) {
            return null;
        }
        double sum = 0;
        for (int i = idx - 3; i <= idx; i++) {
            Double v = series.get(i).v(concept);
            if (v == null) return null;
            sum += v;
        }
        return sum;
    }

    static List<QuarterMetrics> computeMetrics(List<QuarterFin> series) {
        List<QuarterMetrics> out = new ArrayList<>(series.size());
        for (int i = 0; i < series.size(); i++) {
            QuarterFin q = series.get(i);
            QuarterFin prev = i > 0 ? series.get(i - 1) : null;
            QuarterFin yoy = findYoY(series, i);
            QuarterMetrics m = new QuarterMetrics(q.quarter, q.rceptNo, q.derivedQ4);
            Map<String, Object> mm = m.metrics;

            mm.put("valueOrigin", q.derivedQ4 ? "derived_q4" : "reported");

            // 기본 비율
            Double revenue = q.v("revenue"), opIncome = q.v("operatingIncome"), ni = q.v("netIncome");
            Double assets = q.v("totalAssets"), liabilities = q.v("totalLiabilities"), equity = q.v("totalEquity");
            Double cfo = q.v("cfo");
            put(mm, "operatingMargin", div(opIncome, revenue));
            put(mm, "netMargin", div(ni, revenue));
            put(mm, "debtRatio", div(liabilities, equity));                     // 부채비율 (배수)
            put(mm, "currentRatio", div(q.v("currentAssets"), q.v("currentLiabilities")));
            put(mm, "interestCoverage", div(opIncome, q.v("interestExpense")));
            put(mm, "accrualsRatio", assets == null || ni == null || cfo == null
                    ? null : (ni - cfo) / assets);                              // (순이익−CFO)/총자산
            put(mm, "cfoToNetIncome", div(cfo, ni));
            put(mm, "shortTermBorrowings", q.v("shortTermBorrowings"));
            put(mm, "totalEquity", equity);
            put(mm, "netIncome", ni);
            put(mm, "cfo", cfo);
            put(mm, "revenue", revenue);

            // TTM 흐름 지표 (ROE/ROA/회전율은 분기 흐름×시점 잔액이라 TTM 기준)
            Double ttmRevenue = ttm(series, i, "revenue");
            Double ttmNi = ttm(series, i, "netIncome");
            Double ttmOp = ttm(series, i, "operatingIncome");
            Double ttmCfo = ttm(series, i, "cfo");
            Double roa = div(ttmNi, assets);
            Double roe = div(ttmNi, equity);
            Double assetTurnover = div(ttmRevenue, assets);
            put(mm, "roaTtm", roa);
            put(mm, "roeTtm", roe);
            put(mm, "assetTurnoverTtm", assetTurnover);
            put(mm, "cfoTtm", ttmCfo);

            // DuPont 분해: ROE = 순이익률(TTM) × 자산회전율(TTM) × 레버리지
            Map<String, Object> dupont = new LinkedHashMap<>();
            put(dupont, "netMarginTtm", div(ttmNi, ttmRevenue));
            put(dupont, "assetTurnoverTtm", assetTurnover);
            put(dupont, "leverage", div(assets, equity));
            mm.put("dupont", dupont);

            // 운전자본 회전 일수 / CCC (분기 91일 기준, 매출 대용 — COGS 미추출)
            Double dso = mulDays(div(q.v("receivables"), revenue));
            Double dio = mulDays(div(q.v("inventory"), revenue));
            Double dpo = mulDays(div(q.v("payables"), revenue));
            put(mm, "dso", dso);
            put(mm, "dio", dio);
            put(mm, "dpo", dpo);
            put(mm, "ccc", dso == null || dio == null || dpo == null ? null : dso + dio - dpo);

            // Altman Z' (비상장식 — 시가총액 대신 장부 자본)
            put(mm, "altmanZ", altmanZPrime(q, ttmOp, ttmRevenue));

            // Piotroski F (7개 신호 부분 구현 — 신주발행/매출총이익률은 데이터 미추출)
            Map<String, Object> f = piotroski(q, prev, yoy, roa, ttmCfo, ttmNi,
                    yoy != null ? divYoYRoa(series, i) : null);
            mm.put("piotroskiF", f);

            out.add(m);
        }
        appendSelfZScores(out);
        return out;
    }

    private static void put(Map<String, Object> m, String key, Double v) {
        m.put(key, v != null && (v.isNaN() || v.isInfinite()) ? null : v);
    }

    private static Double mulDays(Double ratio) {
        return ratio == null ? null : ratio * 91d;
    }

    private static QuarterFin findYoY(List<QuarterFin> series, int idx) {
        String q = series.get(idx).quarter; // 2026Q1 → 2025Q1
        String target = (Integer.parseInt(q.substring(0, 4)) - 1) + q.substring(4);
        for (QuarterFin candidate : series) {
            if (candidate.quarter.equals(target)) return candidate;
        }
        return null;
    }

    private static Double divYoYRoa(List<QuarterFin> series, int idx) {
        QuarterFin yoy = findYoY(series, idx);
        if (yoy == null) return null;
        int yoyIdx = series.indexOf(yoy);
        return div(ttm(series, yoyIdx, "netIncome"), yoy.v("totalAssets"));
    }

    private static Double altmanZPrime(QuarterFin q, Double ttmOp, Double ttmRevenue) {
        Double ta = q.v("totalAssets"), tl = q.v("totalLiabilities");
        Double ca = q.v("currentAssets"), cl = q.v("currentLiabilities");
        Double re = q.v("retainedEarnings"), equity = q.v("totalEquity");
        if (ta == null || ta == 0d || tl == null || tl == 0d
                || ca == null || cl == null || re == null || equity == null
                || ttmOp == null || ttmRevenue == null) {
            return null;
        }
        double wcTa = (ca - cl) / ta;
        return 0.717 * wcTa + 0.847 * (re / ta) + 3.107 * (ttmOp / ta)
                + 0.420 * (equity / tl) + 0.998 * (ttmRevenue / ta);
    }

    private static Map<String, Object> piotroski(
            QuarterFin q, QuarterFin prev, QuarterFin yoy,
            Double roa, Double ttmCfo, Double ttmNi, Double yoyRoa
    ) {
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("roaPositive", roa == null ? null : roa > 0);
        signals.put("cfoPositive", ttmCfo == null ? null : ttmCfo > 0);
        signals.put("roaImproving", roa == null || yoyRoa == null ? null : roa > yoyRoa);
        signals.put("cfoExceedsNi", ttmCfo == null || ttmNi == null ? null : ttmCfo > ttmNi);
        signals.put("leverageDecreasing", yoyDelta(q, yoy, "totalLiabilities", "totalAssets", false));
        signals.put("liquidityImproving", yoyDelta(q, yoy, "currentAssets", "currentLiabilities", true));
        signals.put("turnoverImproving", yoyDelta(q, yoy, "revenue", "totalAssets", true));

        int score = 0, available = 0;
        for (Object v : signals.values()) {
            if (v instanceof Boolean) {
                available++;
                if ((Boolean) v) score++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", available == 0 ? null : score);
        out.put("maxAvailable", available);
        out.put("signals", signals);
        return out;
    }

    /** (num/den)의 YoY 변화 방향. increasing=true면 증가가 개선. */
    private static Boolean yoyDelta(QuarterFin q, QuarterFin yoy, String num, String den, boolean increasing) {
        if (yoy == null) return null;
        Double cur = div(q.v(num), q.v(den));
        Double base = div(yoy.v(num), yoy.v(den));
        if (cur == null || base == null) return null;
        return increasing ? cur > base : cur < base;
    }

    /** z-score 대상 지표 — 자기 12Q 이동창 기준선 (risk_states 판정에 사용). */
    static final List<String> ZSCORE_METRICS = List.of(
            "currentRatio", "debtRatio", "operatingMargin", "accrualsRatio",
            "assetTurnoverTtm", "interestCoverage", "ccc");

    private static void appendSelfZScores(List<QuarterMetrics> series) {
        for (int i = 0; i < series.size(); i++) {
            Map<String, Object> zscores = new LinkedHashMap<>();
            for (String metric : ZSCORE_METRICS) {
                List<Double> window = new ArrayList<>();
                for (int j = Math.max(0, i - ZSCORE_WINDOW + 1); j <= i; j++) {
                    Double v = series.get(j).get(metric);
                    if (v != null) window.add(v);
                }
                Double current = series.get(i).get(metric);
                if (current == null || window.size() < MIN_QUARTERS_FOR_ZSCORE) {
                    zscores.put(metric, null);
                    continue;
                }
                double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double var = window.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / window.size();
                double std = Math.sqrt(var);
                zscores.put(metric, std == 0d ? 0d : (current - mean) / std);
            }
            series.get(i).metrics.put("zscores12q", zscores);
        }
    }
}
