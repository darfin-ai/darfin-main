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
 * 조회한다. JPA가 아니라 JdbcTemplate을 쓰는 이유: 이 프로젝트는
 * spring.jpa.hibernate.ddl-auto=update라, 엔티티를 만들면 Hibernate가 그
 * 정의에 맞춰 이 테이블들(파이프라인이 소유한 살아있는 데이터)의 스키마를
 * 건드릴 수 있다. 실제로 ddl.sql과 어긋난 entity/analysis 엔티티들이 있었고
 * (어디서도 안 쓰여 2026-07-07 삭제), 같은 사고를 막기 위해 여기서는
 * 엔티티 없이 JdbcTemplate만 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyAnalysisService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DartOverviewService dartOverviewService;

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
                "SELECT c.corp_code, s.company_name, s.stock_code, s.market_type, c.sector, "
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
                    .market((String) row.get("market_type"))
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
                "SELECT c.corp_code, s.company_name, s.stock_code, s.market_type, c.sector, "
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
            return getStockPreview(corpCode);
        }
        // DART 캐시용으로 companies 행만 생긴 browse-only 기업(파이프라인 미실행)은
        // preview 경로로 — 토큰 과금·llm_jobs 등록 없이 dartOverview만 제공.
        if (!hasFilings(corpCode)) {
            return getStockPreview(corpCode);
        }
        Map<String, Object> row = rows.get(0);
        String reprtCode = (String) row.get("reprt_code");
        CompanyResponse company = CompanyResponse.builder()
                .id(corpCode)
                .name((String) row.get("company_name"))
                .ticker((String) row.get("stock_code"))
                .sector((String) row.get("sector"))
                .market((String) row.get("market_type"))
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
            // 대기열에 등록한다(이미 대기 중이면 중복 등록 없이 그대로 둠).
            // Python 파이프라인이 소유하는 테이블이라 JdbcTemplate로 직접
            // INSERT만 한다(JPA 엔티티 없음).
            enqueueOnDemandJob(corpCode);
        }

        return CompanyDetailResponse.builder()
                .company(company)
                .scores(scoreHistory(corpCode))
                .financials(financials(corpCode, true))
                .financialsSeparate(financials(corpCode, false))
                .findings(currentRceptNo != null ? findings(currentRceptNo) : List.of())
                .diffs(currentRceptNo != null ? diffs(currentRceptNo) : List.of())
                .profile(deriveProfile(overview))
                .mdnaHistory(mdnaHistory(overview))
                .recentFilings(recentFilings)
                .overview(overview)
                .dartOverview(dartOverviewService.getDartOverview(corpCode, false))
                .build();
    }

    /**
     * companies에 없어도 stock에 있는 상장 종목은 기본 정보만 담은 preview를 반환한다.
     * 파이프라인 데이터·LLM 큐 등록은 하지 않는다 — 사용자가 My Analysis에 추가하기 전 browse 용도.
     */
    private CompanyDetailResponse getStockPreview(String corpCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT dart_corp_code, company_name, stock_code, market_type "
                        + "FROM stock WHERE dart_corp_code = ? AND stock_code IS NOT NULL",
                corpCode
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        CompanyResponse company = CompanyResponse.builder()
                .id(corpCode)
                .name((String) row.get("company_name"))
                .ticker((String) row.get("stock_code"))
                .market((String) row.get("market_type"))
                .changeSummary("")
                .build();

        return CompanyDetailResponse.builder()
                .company(company)
                .scores(List.of())
                .financials(List.of())
                .financialsSeparate(List.of())
                .findings(List.of())
                .diffs(List.of())
                .recentFilings(List.of())
                .preview(true)
                .dartOverview(dartOverviewService.getDartOverview(corpCode, false))
                .build();
    }

    /**
     * llm_jobs에 이미 pending/running인 job이 있으면 아무것도 하지 않고,
     * 없으면 새로 추가한다. 등록 경로가 이 메서드 하나뿐이라 우선순위 없이
     * 단순 FIFO(등록 순서 = 처리 순서).
     */
    private void enqueueOnDemandJob(String corpCode) {
        try {
            List<Long> existing = jdbcTemplate.queryForList(
                    "SELECT id FROM llm_jobs WHERE corp_code = ? AND status IN ('pending','running')",
                    Long.class, corpCode
            );
            if (existing.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO llm_jobs (corp_code) VALUES (?)", corpCode
                );
            }
        } catch (Exception e) {
            log.warn("llm_jobs enqueue skipped for {}: {}", corpCode, e.getMessage());
        }
    }

    private boolean hasFilings(String corpCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filings WHERE corp_code = ? AND pipeline_status != 'FAILED'",
                Integer.class,
                corpCode
        );
        return count != null && count > 0;
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
     * 없어(findings 라운드에서 범위 제외) 최신 filing의 findings 중 가장
     * 심각한 것의 summary로 대체한다. 예전처럼 llm_summaries 아무 1건을
     * 쓰면 '주식 사항' 섹션의 날짜 나열 같은 원문 조각이 카드에 그대로
     * 노출된다. findings가 아직 없으면(LLM 단계 미완료) 빈 문자열 —
     * 프론트가 대체 문구를 보여준다.
     */
    private String latestChangeSummary(String corpCode) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT fd.summary FROM findings fd "
                        + "JOIN filings f ON f.rcept_no = fd.rcept_no "
                        + "WHERE fd.corp_code = ? "
                        + "ORDER BY f.filed_date DESC, FIELD(fd.severity, 'high', 'medium', 'low'), fd.id "
                        + "LIMIT 1",
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
     * 연결(is_consolidated=1) 기준, (statement_type, 정규화된 account_nm)별로
     * 묶는다. account_nm만 키로 쓰면 '당기순이익'(손익계산서/현금흐름표),
     * '비지배지분'(재무상태표/포괄손익) 같은 동명 계정이 한 시계열로 합쳐져
     * 같은 분기에 값이 2~3개씩 찍히는 톱니 차트가 된다. concept(=DART
     * account_id)은 같은 계정이라도 보고서 종류/연도에 따라 태그가 바뀌거나
     * 비어 있어 키로 못 쓴다(대표값으로만 노출).
     *
     * 분기 축에 올리는 값은 재무제표 종류별로 의미를 맞춘다:
     * - 재무상태표: 시점 수치라 그대로 사용 (사업보고서 행 = Q4 스냅샷).
     * - 손익계산서: 분기보고서의 '3개월' 행 우선. 사업보고서(11011)는 연간
     *   단일값뿐이라 같은 해 3분기 누적이 있으면 Q4 = 연간 − 3분기 누적으로
     *   환산하고, 없으면(분기 데이터가 아예 없는 기준연도) 그 점은 뺀다 —
     *   연간 총액을 분기 축에 그대로 두면 매 Q4가 4배 스파이크로 보인다.
     * - 현금흐름표: 연중 누적치만 공시되므로 분기값 = 당분기 누적 − 직전
     *   분기 누적(Q1은 누적 그대로). 직전 분기 행이 없으면 그 점은 뺀다.
     * 정정공시로 같은 (연도, 보고서) 행이 중복되면 나중에 적재된 행이 이긴다.
     */
    private static final List<String> QUARTER_ORDER = List.of("11013", "11012", "11014", "11011");

    /** 프론트 표시용 재무제표 순서 — 공시 원문의 장(章) 순서와 같다. */
    private static final List<String> STATEMENT_ORDER = List.of("재무상태표", "손익계산서", "현금흐름표");

    private List<FinancialMetricResponse> financials(String corpCode, boolean isConsolidated) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT m.id, m.concept, m.account_nm, m.statement_type, m.ord, m.amount, "
                        + "  m.bsns_year, m.reprt_code, m.period_qualifier "
                        + "FROM metrics m WHERE m.corp_code = ? AND m.is_consolidated = ? "
                        + "ORDER BY m.bsns_year, FIELD(m.reprt_code, '11013', '11012', '11014', '11011'), "
                        + "COALESCE(m.ord, m.id)",
                corpCode, isConsolidated ? 1 : 0
        );
        Map<String, List<Map<String, Object>>> byKey = rows.stream()
                .filter(r -> r.get("amount") != null)
                .collect(Collectors.groupingBy(
                        r -> r.get("statement_type") + "|" + normalizeAccountNm((String) r.get("account_nm")),
                        LinkedHashMap::new, Collectors.toList()));

        // 같은 계정명이 여러 재무제표에 있으면 라벨에 재무제표 종류를 붙여 구분한다.
        Map<String, Long> nameCount = byKey.keySet().stream()
                .collect(Collectors.groupingBy(k -> k.substring(k.indexOf('|') + 1), Collectors.counting()));

        List<FinancialMetricResponse> result = new ArrayList<>();
        // 정렬 키: (재무제표 장 순서, 계정 ord). ord는 fnlttSinglAcntAll이 주는
        // 공시 원문 표 순서라 id 근사보다 정확하다. 구 데이터(ord NULL)만 id로 폴백.
        Map<String, long[]> sortKeyByLabel = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byKey.entrySet()) {
            String statementType = e.getKey().substring(0, e.getKey().indexOf('|'));
            String accountNm = e.getKey().substring(e.getKey().indexOf('|') + 1);
            List<Map<String, Object>> group = e.getValue();

            Map<String, Long> series = buildQuarterSeries(statementType, group);
            if (series.isEmpty()) {
                continue;
            }

            // 표시명: 정규화된 이름 그대로인 원본 표기가 있으면 그것을
            // ('당기순이익(손실)'/'분기순이익'/'반기순이익' 그룹 → '당기순이익'),
            // 없으면 가장 짧은 원본 표기를 쓴다.
            String displayNm = group.stream()
                    .map(r -> (String) r.get("account_nm"))
                    .anyMatch(accountNm::equals)
                    ? accountNm
                    : group.stream()
                            .map(r -> (String) r.get("account_nm"))
                            .min(Comparator.comparingInt(String::length))
                            .orElse(accountNm);
            String label = nameCount.getOrDefault(accountNm, 1L) > 1
                    ? displayNm + " (" + statementType + ")"
                    : displayNm;
            // 태깅이 기간별로 바뀌므로 가장 최근 필딩이 쓴 concept를 대표값으로 노출한다.
            String concept = group.stream()
                    .map(r -> (String) r.get("concept"))
                    .filter(Objects::nonNull)
                    .reduce((first, last) -> last)
                    .orElse(statementType + "_" + accountNm);
            long statementRank = STATEMENT_ORDER.indexOf(statementType);
            long accountOrd = group.stream()
                    .map(r -> r.get("ord"))
                    .filter(Objects::nonNull)
                    .mapToLong(o -> ((Number) o).longValue())
                    .min()
                    .orElseGet(() -> group.stream()
                            .mapToLong(r -> ((Number) r.get("id")).longValue())
                            .min()
                            .orElse(Long.MAX_VALUE));
            sortKeyByLabel.put(label, new long[]{statementRank < 0 ? STATEMENT_ORDER.size() : statementRank, accountOrd});

            result.add(FinancialMetricResponse.builder()
                    .concept(concept)
                    .label(label)
                    .statementType(statementType)
                    .unit("KRW")
                    .series(series.entrySet().stream()
                            .map(p -> FinancialMetricResponse.SeriesPoint.builder()
                                    .quarter(p.getKey())
                                    .value(p.getValue())
                                    .build())
                            .collect(Collectors.toList()))
                    .build());
        }
        result.sort(Comparator
                .comparingLong((FinancialMetricResponse m) -> sortKeyByLabel.get(m.getLabel())[0])
                .thenComparingLong(m -> sortKeyByLabel.get(m.getLabel())[1]));
        return result;
    }

    /**
     * 계정명 표기 변동 정규화 — 같은 계정이 연도/보고서에 따라 '매출액'/
     * '수익(매출액)', '당기순이익'/'당기순이익(손실)'/'분기순이익'/'반기순이익',
     * '영업활동 현금흐름'/'영업활동현금흐름'처럼 갈라져 시계열이 조각난다.
     * 의미가 확실히 같은 변형만 접는다: 공백·각주 마커 제거, '(손실)' 접미
     * 제거, 보고서 기간에 따라 이름이 바뀌는 순이익 계열, '수익(매출액)' 별칭.
     */
    private String normalizeAccountNm(String accountNm) {
        String n = accountNm.replaceAll("\\s*\\(주\\d+\\)", "").replace(" ", "");
        n = n.replace("(손실)", "");
        n = n.replaceAll("^(기본|희석)주당(분기|반기)?순이익$", "$1주당순이익");
        n = n.replaceAll("^(분기|반기)순이익$", "당기순이익");
        if (n.equals("수익(매출액)")) {
            n = "매출액";
        }
        return n;
    }

    /** 분기 라벨("2024Q1") → 값. 입력 group은 (연도, 보고서, id)순 정렬 상태. */
    private Map<String, Long> buildQuarterSeries(String statementType, List<Map<String, Object>> group) {
        // (연도, 보고서)별 대표 행 — 정정공시 중복은 나중 행(id 큰 쪽)이 덮어쓴다.
        Map<String, Map<String, Long>> byYear = new TreeMap<>(); // year → reprt_code → amount
        for (Map<String, Object> r : group) {
            String qualifier = (String) r.get("period_qualifier");
            boolean use = "손익계산서".equals(statementType)
                    ? ("3개월".equals(qualifier) || ("11011".equals(r.get("reprt_code")) && qualifier == null))
                    : qualifier == null;
            if (!use) {
                continue;
            }
            byYear.computeIfAbsent((String) r.get("bsns_year"), k -> new HashMap<>())
                    .put((String) r.get("reprt_code"), ((Number) r.get("amount")).longValue());
        }
        // 손익 Q4 환산용: 3분기 누적(11014, '누적') 값.
        Map<String, Long> q3Cumulative = new HashMap<>();
        if ("손익계산서".equals(statementType)) {
            for (Map<String, Object> r : group) {
                if ("누적".equals(r.get("period_qualifier")) && "11014".equals(r.get("reprt_code"))) {
                    q3Cumulative.put((String) r.get("bsns_year"), ((Number) r.get("amount")).longValue());
                }
            }
        }

        Map<String, Long> series = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> yearEntry : byYear.entrySet()) {
            String year = yearEntry.getKey();
            Map<String, Long> byReprt = yearEntry.getValue();
            Long prevCumulative = null; // 현금흐름표 분기 환산용
            for (String reprtCode : QUARTER_ORDER) {
                Long amount = byReprt.get(reprtCode);
                if (amount == null) {
                    prevCumulative = null; // 중간 분기가 비면 이후 누적 차감이 불가능
                    continue;
                }
                String quarter = quarterLabel(year, reprtCode);
                if ("손익계산서".equals(statementType)) {
                    if ("11011".equals(reprtCode)) {
                        Long q3 = q3Cumulative.get(year);
                        if (q3 != null) {
                            series.put(quarter, amount - q3);
                        }
                    } else {
                        series.put(quarter, amount);
                    }
                } else if ("현금흐름표".equals(statementType)) {
                    if ("11013".equals(reprtCode)) {
                        series.put(quarter, amount);
                    } else if (prevCumulative != null) {
                        series.put(quarter, amount - prevCumulative);
                    }
                    prevCumulative = amount;
                } else {
                    series.put(quarter, amount); // 재무상태표 등 시점 수치
                }
            }
        }
        return series;
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
     * company_overview의 mdnaHistory는 경영진 설명(MD&A)이 실제로 있는 filing을
     * 시간순으로 나열한 목록(LLM 판정 없음, 결정론적 — Python: overview.py:
     * build_mdna_entry)이라 결정론적 1단계에서 이미 채워져 있다. 필드 자체가
     * 없으면(1단계 이전 구버전 행) 빈 리스트 — 프론트는 이 경우 기존 "사업의
     * 내용" 카드로 폴백한다.
     */
    @SuppressWarnings("unchecked")
    private List<Object> mdnaHistory(Object overview) {
        if (!(overview instanceof Map)) {
            return List.of();
        }
        Object history = ((Map<String, Object>) overview).get("mdnaHistory");
        return history instanceof List ? (List<Object>) history : List.of();
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
