package com.kosta.darfin.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.dto.analysis.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * darfin-company-analysis(Python) 파이프라인이 채운 9개 테이블을 읽기 전용으로
 * 조회한다. JPA가 아니라 JdbcTemplate을 쓰는 이유: entity/analysis의 기존
 * 엔티티(Metrics/TextChunks 등)가 실제 ddl.sql과 어긋나 있고, 이 프로젝트는
 * spring.jpa.hibernate.ddl-auto=update라 Hibernate가 그 엔티티들에 맞춰 이
 * 테이블(파이프라인이 실제로 쓰는 살아있는 데이터)을 건드릴 위험이 있다 —
 * JdbcTemplate은 이 테이블들에 Hibernate가 전혀 관여하지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyAnalysisService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> REPRT_TYPE_LABEL = Map.of(
            "11011", "사업보고서",
            "11012", "반기보고서",
            "11013", "분기보고서",
            "11014", "분기보고서"
    );

    private static final Map<String, String> REPRT_QUARTER_SUFFIX = Map.of(
            "11011", "Q4",
            "11012", "Q2",
            "11013", "Q1",
            "11014", "Q3"
    );

    private static final Map<String, String> REPRT_PERIOD_LABEL = Map.of(
            "11011", "연간",
            "11012", "반기",
            "11013", "1분기",
            "11014", "3분기"
    );

    private String quarterLabel(String bsnsYear, String reprtCode) {
        return bsnsYear + REPRT_QUARTER_SUFFIX.get(reprtCode);
    }

    public List<CompanyListItemResponse> listCompanies() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.corp_code, s.company_name, s.stock_code, c.sector, "
                        + "  f.reprt_code, f.bsns_year, f.filed_date "
                        + "FROM companies c "
                        + "JOIN stock s ON s.dart_corp_code = c.corp_code "
                        + "LEFT JOIN ( "
                        + "  SELECT corp_code, reprt_code, bsns_year, filed_date, "
                        + "         ROW_NUMBER() OVER (PARTITION BY corp_code ORDER BY filed_date DESC) rn "
                        + "  FROM filings WHERE pipeline_status != 'FAILED' "
                        + ") f ON f.corp_code = c.corp_code AND f.rn = 1"
        );

        List<CompanyListItemResponse> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String corpCode = (String) row.get("corp_code");
            String reprtCode = (String) row.get("reprt_code");

            CompanyResponse company = CompanyResponse.builder()
                    .id(corpCode)
                    .name((String) row.get("company_name"))
                    .ticker((String) row.get("stock_code"))
                    .sector((String) row.get("sector"))
                    .latestFilingType(reprtCode != null ? REPRT_TYPE_LABEL.get(reprtCode) : null)
                    .latestFilingDate(formatFiledDate((String) row.get("filed_date")))
                    .changeSummary(latestChangeSummary(corpCode))
                    .build();

            result.add(CompanyListItemResponse.builder()
                    .company(company)
                    .scores(scoreHistory(corpCode))
                    .build());
        }
        return result;
    }

    /**
     * corp_code가 존재하지 않으면 null (컨트롤러가 404로 매핑) — 회사가 없는
     * 경우와 회사는 있지만 파이프라인이 아직 처리하지 않은 경우를 프론트가
     * 구분해야 해서 여기서 예외를 던지지 않고 null로 신호를 준다.
     */
    public CompanyDetailResponse getCompanyDetail(String corpCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.corp_code, s.company_name, s.stock_code, c.sector, "
                        + "  f.reprt_code, f.bsns_year, f.filed_date "
                        + "FROM companies c "
                        + "JOIN stock s ON s.dart_corp_code = c.corp_code "
                        + "LEFT JOIN ( "
                        + "  SELECT corp_code, reprt_code, bsns_year, filed_date, "
                        + "         ROW_NUMBER() OVER (PARTITION BY corp_code ORDER BY filed_date DESC) rn "
                        + "  FROM filings WHERE pipeline_status != 'FAILED' "
                        + ") f ON f.corp_code = c.corp_code AND f.rn = 1 "
                        + "WHERE c.corp_code = ?",
                corpCode
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String reprtCode = (String) row.get("reprt_code");
        CompanyResponse company = CompanyResponse.builder()
                .id(corpCode)
                .name((String) row.get("company_name"))
                .ticker((String) row.get("stock_code"))
                .sector((String) row.get("sector"))
                .latestFilingType(reprtCode != null ? REPRT_TYPE_LABEL.get(reprtCode) : null)
                .latestFilingDate(formatFiledDate((String) row.get("filed_date")))
                .changeSummary(latestChangeSummary(corpCode))
                .build();

        String currentRceptNo = currentFiling(corpCode);
        Object overview = currentRceptNo != null ? overviewJson(currentRceptNo) : null;
        List<RecentFilingResponse> recentFilings = recentFilings(corpCode);

        if (!isAiInsightsReady(overview)) {
            // overview 자체가 없거나(파이프라인이 이 회사를 아예 처리 안 함),
            // 있어도 aiInsightsReady=false(결정론적 패널만 채워지고 findings/
            // risks/insight 같은 LLM 산출물은 아직) — 두 경우 다 LLM 처리
            // 대기열에 최우선순위(on_demand)로 올려서, 다음 워커 실행 때
            // 다른 예약 작업보다 먼저 처리되게 한다. Python 파이프라인이
            // 소유하는 테이블이라 JdbcTemplate로 직접 INSERT만 한다(JPA
            // 엔티티 없음).
            enqueueOnDemandJob(corpCode);
        }

        return CompanyDetailResponse.builder()
                .company(company)
                .scores(scoreHistory(corpCode))
                .financials(financials(corpCode))
                .findings(currentRceptNo != null ? findings(currentRceptNo) : List.of())
                .diffs(currentRceptNo != null ? diffs(currentRceptNo) : List.of())
                .profile(deriveProfile(overview))
                .strategyShifts(strategyShifts(overview))
                .recentFilings(recentFilings)
                .overview(overview)
                .build();
    }

    /**
     * llm_jobs에 이미 pending/running인 job이 있으면 priority=0(on_demand)으로
     * 승격, 없으면 새로 추가한다. dart_pipeline.db: enqueue_llm_job()과 동일한
     * "더 급한 쪽으로만 갱신" 로직을 SQL로 재현.
     */
    private void enqueueOnDemandJob(String corpCode) {
        List<Long> existing = jdbcTemplate.queryForList(
                "SELECT id FROM llm_jobs WHERE corp_code = ? AND status IN ('pending','running')",
                Long.class, corpCode
        );
        if (existing.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO llm_jobs (corp_code, priority) VALUES (?, 0)", corpCode
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE llm_jobs SET priority = 0 WHERE id = ?", existing.get(0)
            );
        }
    }

    // ── 공통 조회 ────────────────────────────────────────────────────────

    private String currentFiling(String corpCode) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT rcept_no FROM filings "
                        + "WHERE corp_code = ? AND pipeline_status IN ('DIFFED','SUMMARIZED') "
                        + "ORDER BY filed_date DESC LIMIT 1",
                String.class, corpCode
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 그리드 카드용 changeSummary — 파이프라인에 전용 1줄 요약 산출물이 아직
     * 없어(findings 라운드에서 범위 제외) 최신 filing의 서술형 llm_summaries
     * 아무 1건으로 임시 대체한다. 알려진 한계.
     */
    private String latestChangeSummary(String corpCode) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT ls.content FROM llm_summaries ls "
                        + "JOIN filings f ON f.rcept_no = ls.rcept_no "
                        + "WHERE ls.corp_code = ? ORDER BY f.filed_date DESC, ls.id DESC LIMIT 1",
                String.class, corpCode
        );
        return rows.isEmpty() ? "" : rows.get(0);
    }

    private List<ScoreComponentResponse> scoreHistory(String corpCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT quarter, component, value, max_points FROM score_history "
                        + "WHERE corp_code = ? ORDER BY component, quarter",
                corpCode
        );
        Map<String, List<ScoreComponentResponse.HistoryPoint>> byComponent = new LinkedHashMap<>();
        Map<String, Integer> maxPoints = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String component = (String) row.get("component");
            byComponent.computeIfAbsent(component, k -> new ArrayList<>())
                    .add(ScoreComponentResponse.HistoryPoint.builder()
                            .quarter((String) row.get("quarter"))
                            .value(((Number) row.get("value")).doubleValue())
                            .build());
            maxPoints.put(component, ((Number) row.get("max_points")).intValue());
        }
        return byComponent.entrySet().stream()
                .map(e -> ScoreComponentResponse.builder()
                        .key(e.getKey())
                        .maxPoints(maxPoints.get(e.getKey()))
                        .history(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 연결(is_consolidated=1) 기준, account_nm별로 묶는다. concept(=DART
     * account_id)은 같은 계정이라도 보고서 종류/연도에 따라 dart_* 확장 태그와
     * ifrs-full_* 표준 태그를 오가거나 아예 비어(표준계정코드 미사용) 있어서
     * 시계열 묶음 키로 쓰면 한 계정이 여러 조각으로 쪼개진다(자산총계처럼
     * 항상 같은 표준 concept로 태깅되는 상위 합계 항목만 안 쪼개짐). account_nm은
     * metrics.py에서 이미 strip()되어 있어 안정적인 키다.
     * 손익/현금흐름은 period_qualifier='3개월'(분기 단일값) 우선, 없으면(=재무
     * 상태표류, 시점 수치) NULL 행을 사용 — 누적치와 섞지 않는다.
     */
    private List<FinancialMetricResponse> financials(String corpCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT m.concept, m.account_nm, m.amount, m.bsns_year, m.reprt_code, m.period_qualifier "
                        + "FROM metrics m WHERE m.corp_code = ? AND m.is_consolidated = 1 "
                        + "AND (m.period_qualifier = '3개월' OR m.period_qualifier IS NULL) "
                        + "ORDER BY m.bsns_year, FIELD(m.reprt_code, '11013', '11012', '11014', '11011')",
                corpCode
        );
        Map<String, List<Map<String, Object>>> byKey = rows.stream()
                .collect(Collectors.groupingBy(r -> (String) r.get("account_nm"),
                        LinkedHashMap::new, Collectors.toList()));

        List<FinancialMetricResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byKey.entrySet()) {
            List<Map<String, Object>> group = e.getValue();
            String label = e.getKey();
            // 태깅이 기간별로 바뀌므로 가장 최근 필딩이 쓴 concept를 대표값으로 노출한다.
            String concept = group.stream()
                    .map(r -> (String) r.get("concept"))
                    .filter(Objects::nonNull)
                    .reduce((first, last) -> last)
                    .orElse(null);
            List<FinancialMetricResponse.SeriesPoint> series = group.stream()
                    .map(r -> FinancialMetricResponse.SeriesPoint.builder()
                            .quarter(quarterLabel((String) r.get("bsns_year"), (String) r.get("reprt_code")))
                            .value(((Number) r.get("amount")).longValue())
                            .build())
                    .collect(Collectors.toList());
            result.add(FinancialMetricResponse.builder()
                    .concept(concept)
                    .label(label)
                    .unit("KRW")
                    .series(series)
                    .build());
        }
        return result;
    }

    private List<FindingResponse> findings(String rceptNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, severity, score_component, summary, hops_json FROM findings WHERE rcept_no = ?",
                rceptNo
        );
        return rows.stream()
                .map(r -> FindingResponse.builder()
                        .id(String.valueOf(r.get("id")))
                        .severity((String) r.get("severity"))
                        .scoreComponent((String) r.get("score_component"))
                        .summary((String) r.get("summary"))
                        .hops(parseJson((String) r.get("hops_json")))
                        .build())
                .collect(Collectors.toList());
    }

    private List<SectionDiffEntryResponse> diffs(String rceptNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT canonical_label, source_label, comparison_type, change_type, "
                        + "  before_text, after_text, metrics_json, source_ref "
                        + "FROM section_diffs WHERE rcept_no = ?",
                rceptNo
        );
        return rows.stream()
                .map(r -> SectionDiffEntryResponse.builder()
                        .sectionLabel((String) r.get("canonical_label"))
                        .sourceLabel((String) r.get("source_label"))
                        .comparisonType((String) r.get("comparison_type"))
                        .changeType((String) r.get("change_type"))
                        .before((String) r.get("before_text"))
                        .after((String) r.get("after_text"))
                        .metrics(parseJson((String) r.get("metrics_json")))
                        .sourceRef((String) r.get("source_ref"))
                        .build())
                .collect(Collectors.toList());
    }

    private Object overviewJson(String rceptNo) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT overview_json FROM company_overview WHERE rcept_no = ?", String.class, rceptNo
        );
        return rows.isEmpty() ? null : parseJson(rows.get(0));
    }

    private List<RecentFilingResponse> recentFilings(String corpCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT rcept_no, reprt_code, bsns_year, filed_date FROM filings "
                        + "WHERE corp_code = ? AND pipeline_status != 'FAILED' "
                        + "ORDER BY filed_date DESC LIMIT 8",
                corpCode
        );
        return rows.stream()
                .map(r -> {
                    String rceptNo = (String) r.get("rcept_no");
                    String reprtCode = (String) r.get("reprt_code");
                    String bsnsYear = (String) r.get("bsns_year");
                    String periodSuffix = REPRT_PERIOD_LABEL.get(reprtCode);
                    return RecentFilingResponse.builder()
                            .id(rceptNo)
                            .type(REPRT_TYPE_LABEL.get(reprtCode))
                            .period(bsnsYear + " " + periodSuffix)
                            .date(formatFiledDate((String) r.get("filed_date")))
                            .dartUrl("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + rceptNo)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * overview가 없거나(파이프라인 미처리), 있어도 aiInsightsReady가
     * false(1단계 결정론적 패널만 채워지고 findings/risks/insight 등 LLM
     * 산출물은 아직)면 false. 필드 자체가 없는 구버전 행(이번 세션 초기에
     * 원자적으로 완성된 삼성전자/SK하이닉스 등)은 이미 완료된 것으로
     * 간주(하위 호환) — dart_pipeline.db: filings_for_ai_insights()와
     * 동일한 규칙.
     */
    @SuppressWarnings("unchecked")
    private boolean isAiInsightsReady(Object overview) {
        if (!(overview instanceof Map)) {
            return false;
        }
        Object flag = ((Map<String, Object>) overview).get("aiInsightsReady");
        return !Boolean.FALSE.equals(flag);
    }

    /**
     * company_overview에서 최소한으로 파생 — 파이프라인이 아직 profile 전용
     * 필드를 만들지 않아서(알려진 한계). governanceNotes는 생성 로직이 없어 빈 문자열.
     */
    @SuppressWarnings("unchecked")
    private CompanyProfileResponse deriveProfile(Object overview) {
        if (!(overview instanceof Map)) {
            return CompanyProfileResponse.builder()
                    .businessDescription("").shareStructure("").governanceNotes("").build();
        }
        Map<String, Object> map = (Map<String, Object>) overview;

        String businessDescription = "";
        Object segments = map.get("segments");
        if (segments instanceof List) {
            businessDescription = ((List<Map<String, Object>>) segments).stream()
                    .map(s -> s.get("name") + "(" + s.get("description") + ")")
                    .collect(Collectors.joining(", "));
        }

        String shareStructure = "";
        Object shareholders = map.get("shareholders");
        if (shareholders instanceof List && !((List<?>) shareholders).isEmpty()) {
            Map<String, Object> top = (Map<String, Object>) ((List<?>) shareholders).get(0);
            shareStructure = top.get("name") + " 외 특수관계인, 지분율 " + top.get("share") + "%";
        }

        return CompanyProfileResponse.builder()
                .businessDescription(businessDescription)
                .shareStructure(shareStructure)
                .governanceNotes("")
                .build();
    }

    /**
     * company_overview의 strategyShifts는 filing 단위가 아니라 회사 전체
     * 역사를 한 번에 보고 계산돼(Python: strategy_shifts_ingest.py) 최신
     * filing의 overview_json에만 patch돼 있다. 아직 계산 전(필드 없음)이면
     * 빈 리스트 — 프론트는 이 경우 기존 "사업의 내용" 카드로 폴백한다.
     */
    @SuppressWarnings("unchecked")
    private List<Object> strategyShifts(Object overview) {
        if (!(overview instanceof Map)) {
            return List.of();
        }
        Object shifts = ((Map<String, Object>) overview).get("strategyShifts");
        return shifts instanceof List ? (List<Object>) shifts : List.of();
    }

    private Object parseJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("JSON 파싱 실패, 원본 문자열로 대체: {}", e.getMessage());
            return json;
        }
    }

    private String formatFiledDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) return yyyymmdd;
        return yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6, 8);
    }
}
