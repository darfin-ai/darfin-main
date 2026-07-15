package com.kosta.darfin.service.analysis;

import com.kosta.darfin.dto.analysis.AiAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI분석 Layer 1 오케스트레이션 — 재무추이와 동일한 on-demand 패턴.
 * 요청 시점에 financial_facts read-through(5년 lookback)로 분기 시계열을 얻고,
 * MetricsCalculator(비율/점수) → RiskStateMachine(상태 전이)을 돌려
 * derived_metrics/risk_states에 캐시한다. 전 종목 backfill 없음 — 처음 보는
 * 기업의 첫 조회만 콜드(추가 DART 호출 ~수 초), 이후는 캐시.
 *
 * LLM은 여기서 절대 호출하지 않는다. 텍스트 레이어(narrative/watch_next/
 * text_signals)는 llm_jobs(job_type='risk_analysis')에 등록만 하고 Python
 * 워커가 비동기로 채운다 — 프론트는 quant 결과를 즉시 그리고 폴링으로 보강.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAnalysisService {

    /**
     * 상태머신 이력용 lookback ~5년(20분기). 재무추이의 730일보다 넓다 —
     * Piotroski YoY(≥8Q)와 한국 경기순환 업종(반도체/조선/화학)의 3~5년 주기,
     * 추세-노이즈 구분(~12Q)을 위해. 캐시 테이블은 공유라 첫 조회 이후 무료.
     */
    private static final int LOOKBACK_DAYS = 1825;

    private final JdbcTemplate jdbcTemplate;
    private final FinancialFactsService financialFactsService;
    private final RiskAnalysisDao riskAnalysisDao;
    private final OnboardIngestQueue onboardIngestQueue;

    /**
     * @return null이면 stock에도 없는 corp_code (컨트롤러가 404).
     *
     * 파이프라인 온보딩(filings) 여부와 무관하게 quant 레이어는 항상 계산한다 —
     * 입력이 financial_facts read-through뿐이라 어떤 상장사든 on-demand로 동작
     * (콜드 기업 첫 조회만 DART 호출 수 초). filings는 텍스트 레이어(추출/내러티브)
     * 에만 필요하므로 risk job 등록만 filings 존재에 걸어둔다 — 열람권 구매가
     * companies에 온보딩하고, 다음 daily scan이 filings를 채우면 그때부터
     * 내러티브가 따라온다.
     */
    public AiAnalysisResponse getAiAnalysis(String corpCode) {
        if (!isKnownStock(corpCode)) {
            return null;
        }

        // 1) 분기 시계열 (read-through, 5년 창). 연결 우선, 없으면 별도 폴백 —
        //    fs_div는 한 시계열에 절대 섞지 않는다.
        List<Map<String, Object>> accountRows = financialFactsService.liveMetricRows(corpCode, LOOKBACK_DAYS);
        String fsDiv = "CFS";
        List<MetricsCalculator.QuarterMetrics> series = MetricsCalculator.compute(accountRows, true);
        if (series.isEmpty()) {
            fsDiv = "OFS";
            series = MetricsCalculator.compute(accountRows, false);
        }
        if (series.isEmpty()) {
            return AiAnalysisResponse.builder()
                    .status("insufficient_data").fsDiv(null)
                    .quarters(List.of()).currentStates(List.of()).trajectories(List.of())
                    .metricsSeries(List.of()).dossierEvents(List.of())
                    .build();
        }

        // 2) 상태머신 + 캐시 upsert. 최신 분기 근거 공시가 캐시와 같으면 쓰기 생략
        //    (계산 자체는 순수 함수라 재실행 비용이 무시할 수준 — staleness 체크는
        //    DB 쓰기 절약용이고, 정정공시로 rcept_no가 바뀌면 자동 재기록된다).
        List<RiskStateMachine.StateRow> states = RiskStateMachine.run(series);
        String latestRcept = series.get(series.size() - 1).rceptNo;
        boolean fresh = latestRcept != null
                && latestRcept.equals(riskAnalysisDao.latestComputedRceptNo(corpCode, fsDiv));
        if (!fresh) {
            persist(corpCode, fsDiv, series, states);
        }

        // 3) 텍스트 레이어(내러티브)는 Python 워커 담당 — 준비 안 됐으면 큐 등록.
        //    워커 입력(text_chunks)은 filings에서 나오므로 파이프라인이 아직 이
        //    회사를 수집 안 했으면(filings 없음) 등록하지 않는다 — 다음 daily
        //    scan이 채운 뒤 재조회 때 등록된다.
        List<RiskAnalysisDao.RiskStateRow> stored = riskAnalysisDao.riskStates(corpCode);
        String latestQuarter = series.get(series.size() - 1).quarter;
        boolean narrativesReady = latestNarrativesReady(stored, latestQuarter);
        boolean insufficient = series.size() < RiskStateMachine.MIN_QUARTERS;
        boolean hasFilings = hasFilings(corpCode);
        RiskAnalysisDao.RiskJobStatus job = riskAnalysisDao.latestRiskJobStatus(corpCode);
        if (!narrativesReady && !insufficient) {
            if (hasFilings) {
                // Failed jobs remain visible to the client instead of being immediately
                // hidden by an automatic retry. A future explicit retry action can enqueue.
                if (job == null || "done".equals(job.getStatus())) {
                    riskAnalysisDao.enqueueRiskJob(corpCode);
                    job = riskAnalysisDao.latestRiskJobStatus(corpCode);
                }
            } else {
                // 자가치유: 이 endpoint는 열람권 보유자만 도달하므로(컨트롤러가
                // 이미 확인) "관심 없다"로 오판할 위험이 없다 — filings가 아직
                // 없으면 backfill부터 재시도한다.
                onboardIngestQueue.enqueueIfNeeded(corpCode);
            }
        }

        String status = responseStatus(insufficient, narrativesReady, job);
        return buildResponse(fsDiv, series, states, stored, status,
                job != null ? job.isRetryable() : null,
                job != null ? job.getErrorCode() : null,
                riskAnalysisDao.dossierEvents(corpCode));
    }

    /** Explicit user retry after a failed narrative job; enqueue remains idempotent. */
    public AiAnalysisResponse retryAiAnalysis(String corpCode) {
        if (!isKnownStock(corpCode)) {
            return null;
        }
        riskAnalysisDao.enqueueRiskJob(corpCode);
        return getAiAnalysis(corpCode);
    }

    static boolean latestNarrativesReady(List<RiskAnalysisDao.RiskStateRow> rows, String latestQuarter) {
        return RiskStateMachine.CATEGORIES.stream().allMatch(category -> rows.stream()
                .anyMatch(row -> latestQuarter.equals(row.getQuarter())
                        && category.equals(row.getCategory()) && row.isNarrativeFresh()));
    }

    static String responseStatus(boolean insufficient, boolean narrativesReady,
                                 RiskAnalysisDao.RiskJobStatus job) {
        if (insufficient) return "insufficient_data";
        if (narrativesReady) return "complete";
        if (job == null) return "quant_only";
        if ("failed".equals(job.getStatus())) return "failed";
        if ("pending".equals(job.getStatus()) || "running".equals(job.getStatus())) {
            return "generating_narrative";
        }
        return "quant_only";
    }

    /** correction_material 판정 대상 지표와 임계값(placeholder — 팀 결정 대상, plan §8). */
    private static final List<String> CORRECTION_WATCH_METRICS =
            List.of("revenue", "netIncome", "totalEquity", "cfo");
    private static final double CORRECTION_MATERIALITY = 0.05; // |Δ| > 5%

    private void persist(String corpCode, String fsDiv,
                         List<MetricsCalculator.QuarterMetrics> series,
                         List<RiskStateMachine.StateRow> states) {
        Map<String, RiskAnalysisDao.DerivedMetricsSnapshot> previous =
                riskAnalysisDao.metricsSnapshots(corpCode, fsDiv);
        for (MetricsCalculator.QuarterMetrics q : series) {
            detectMaterialCorrection(corpCode, q, previous.get(q.quarter));
            riskAnalysisDao.upsertDerivedMetrics(corpCode, q.quarter, fsDiv, q.rceptNo, q.metrics);
        }
        for (RiskStateMachine.StateRow s : states) {
            riskAnalysisDao.upsertRiskState(corpCode, s.quarter, s.category, s.state, s.consecutiveQtrs, s.signals);
        }
    }

    /**
     * 정정공시(같은 분기, 새 rcept_no)가 핵심 수치를 유의미하게 바꿨으면
     * correction_material 이벤트 — 결정론적, LLM 불필요. 단일 공시 분석은
     * 구조적으로 못 잡는 시계열 신호다. restatement_gap(재작성 비교치 차이)은
     * fnlttSinglAcntAll의 전기(frmtrm) 컬럼 추출이 필요해 후속 과제.
     */
    private void detectMaterialCorrection(String corpCode, MetricsCalculator.QuarterMetrics current,
                                          RiskAnalysisDao.DerivedMetricsSnapshot stored) {
        if (stored == null || stored.getRceptNo() == null
                || current.rceptNo == null || current.rceptNo.equals(stored.getRceptNo())) {
            return;
        }
        for (String metric : CORRECTION_WATCH_METRICS) {
            Double now = current.get(metric);
            Object beforeRaw = stored.getMetrics().get(metric);
            Double before = beforeRaw instanceof Number ? ((Number) beforeRaw).doubleValue() : null;
            if (now == null || before == null || before == 0d) continue;
            double change = Math.abs(now - before) / Math.abs(before);
            if (change > CORRECTION_MATERIALITY) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("quarter", current.quarter);
                detail.put("metric", metric);
                detail.put("before", before);
                detail.put("after", now);
                detail.put("changeRatio", change);
                detail.put("supersededRceptNo", stored.getRceptNo());
                riskAnalysisDao.insertDossierEventIfAbsent(
                        corpCode, current.rceptNo, "correction_material", "earnings_quality",
                        current.quarter + ":" + metric, detail);
            }
        }
    }

    private AiAnalysisResponse buildResponse(
            String fsDiv,
            List<MetricsCalculator.QuarterMetrics> series,
            List<RiskStateMachine.StateRow> states,
            List<RiskAnalysisDao.RiskStateRow> stored,
            String status,
            Boolean retryable,
            String errorCode,
            List<RiskAnalysisDao.DossierEventRow> events
    ) {
        List<String> quarters = series.stream().map(q -> q.quarter).collect(Collectors.toList());
        String latestQuarter = quarters.get(quarters.size() - 1);

        // Python이 채운 narrative/watch_next를 (quarter, category)로 병합.
        Map<String, RiskAnalysisDao.RiskStateRow> storedByKey = stored.stream()
                .collect(Collectors.toMap(r -> r.getQuarter() + "|" + r.getCategory(), r -> r, (a, b) -> b));

        List<AiAnalysisResponse.CategoryState> currentStates = new ArrayList<>();
        Map<String, List<AiAnalysisResponse.TrajectoryPoint>> trajectoryByCategory = new LinkedHashMap<>();
        for (String category : RiskStateMachine.CATEGORIES) {
            trajectoryByCategory.put(category, new ArrayList<>());
        }
        for (RiskStateMachine.StateRow s : states) {
            trajectoryByCategory.get(s.category).add(AiAnalysisResponse.TrajectoryPoint.builder()
                    .quarter(s.quarter).state(s.state).consecutiveQtrs(s.consecutiveQtrs).build());
            if (s.quarter.equals(latestQuarter)) {
                RiskAnalysisDao.RiskStateRow db = storedByKey.get(s.quarter + "|" + s.category);
                currentStates.add(AiAnalysisResponse.CategoryState.builder()
                        .category(s.category)
                        .state(s.state)
                        .consecutiveQtrs(s.consecutiveQtrs)
                        .narrativeKo(db != null ? db.getNarrativeKo() : null)
                        .watchNextKo(db != null ? db.getWatchNextKo() : null)
                        .signals(s.signals)
                        .build());
            }
        }

        return AiAnalysisResponse.builder()
                .status(status)
                .retryable("failed".equals(status) ? retryable : null)
                .errorCode("failed".equals(status) ? errorCode : null)
                .fsDiv(fsDiv)
                .preview(false)
                .quarters(quarters)
                .currentStates(currentStates)
                .trajectories(trajectoryByCategory.entrySet().stream()
                        .map(e -> AiAnalysisResponse.CategoryTrajectory.builder()
                                .category(e.getKey()).points(e.getValue()).build())
                        .collect(Collectors.toList()))
                .metricsSeries(series.stream()
                        .map(q -> AiAnalysisResponse.QuarterMetricsEntry.builder()
                                .quarter(q.quarter).metrics(q.metrics).build())
                        .collect(Collectors.toList()))
                .dossierEvents(events.stream()
                        .map(e -> AiAnalysisResponse.DossierEvent.builder()
                                .rceptNo(e.getRceptNo()).eventType(e.getEventType())
                                .category(e.getCategory()).itemKey(e.getItemKey())
                                .detail(e.getDetail()).createdAt(e.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    // ── 게이트 판정 (CompanyAnalysisService.getCompanyDetail의 preview 규칙과 동일) ──

    private boolean isKnownStock(String corpCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock WHERE dart_corp_code = ? AND stock_code IS NOT NULL",
                Integer.class, corpCode);
        return count != null && count > 0;
    }

    /** 파이프라인이 이 회사의 공시를 수집했는지 — 텍스트 레이어(risk job) 전제 조건. */
    private boolean hasFilings(String corpCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filings WHERE corp_code = ? AND pipeline_status != 'FAILED'",
                Integer.class, corpCode);
        return count != null && count > 0;
    }
}
