package com.kosta.darfin.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

/**
 * report_facts 테이블 접근 계층. darfin-company-analysis(Python) 파이프라인도 이 테이블을
 * 쓰기 때문에 JPA 엔티티를 두지 않고 JdbcTemplate만 사용한다 — CompanyAnalysisService.java의
 * 문서 주석에 있는 것과 동일한 근거 (spring.jpa.hibernate.ddl-auto=update가 파이프라인 소유
 * 스키마를 건드릴 위험).
 *
 * <p>이 DAO는 payload가 placeholder인지 판단하지 않는다 — 호출자(DartOverviewService)가
 * Mapper.isPlaceholderOnly()로 먼저 판단하고 null 여부를 결정해서 넘긴다. Python의
 * dart_overview_rt.py:118-126 (payload=None if is_placeholder_only(outcome) else outcome)과
 * 동일한 책임 분리.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReportFactDao {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Getter
    @RequiredArgsConstructor
    public static class ReportFactCacheEntry {
        private final String apiId;
        private final String rceptNo;
        private final List<Map<String, Object>> payload;
        private final Instant fetchedAt;
    }

    @Getter
    @RequiredArgsConstructor
    public static class PeriodKey {
        private final String bsnsYear;
        private final String reprtCode;
    }

    /**
     * FK bootstrap: report_facts.corp_code references companies(corp_code). Browse-only stocks
     * (not yet onboarded into the pipeline) may not have a companies row yet.
     * Throws IllegalStateException if corp_code isn't in `stock` either (mirrors Python's ValueError
     * behavior in dart_overview_rt.py's _ensure_company_for_cache).
     */
    public void ensureCompanyForCache(String corpCode) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM companies WHERE corp_code = ?", Boolean.class, corpCode);
        if (Boolean.TRUE.equals(exists)) return;

        List<Map<String, Object>> stockRows = jdbcTemplate.queryForList(
                "SELECT company_name, stock_code FROM stock WHERE dart_corp_code = ?", corpCode);
        if (stockRows.isEmpty()) {
            throw new IllegalStateException("corp_code " + corpCode + " not found in stock table");
        }
        jdbcTemplate.update("INSERT IGNORE INTO companies (corp_code) VALUES (?)", corpCode);
    }

    /** api_id -> cached row, for the given period. payload is null when payload_json IS NULL (negative cache). */
    public Map<String, ReportFactCacheEntry> reportFactsForPeriod(String corpCode, String bsnsYear, String reprtCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT api_id, payload_json, rcept_no, fetched_at FROM report_facts "
                        + "WHERE corp_code = ? AND bsns_year = ? AND reprt_code = ?",
                corpCode, bsnsYear, reprtCode);
        Map<String, ReportFactCacheEntry> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String apiId = (String) row.get("api_id");
            String payloadJson = (String) row.get("payload_json");
            List<Map<String, Object>> payload = payloadJson == null ? null : readPayload(payloadJson);
            java.sql.Timestamp fetchedAt = (java.sql.Timestamp) row.get("fetched_at");
            out.put(apiId, new ReportFactCacheEntry(
                    apiId, (String) row.get("rcept_no"), payload, fetchedAt.toInstant()));
        }
        return out;
    }

    /**
     * Deletes report_facts rows for corpCode whose (bsns_year, reprt_code) isn't in keepPeriods.
     * Safety guard: if keepPeriods is empty, deletes nothing (never wipe all cache for a corp_code
     * as a side effect of an empty candidate list — this should not happen in the real call path,
     * see DartOverviewService, but the DAO must not assume its caller got that right). Mirrors
     * db.py's delete_report_facts_outside_periods early-return on an empty keep_periods list.
     */
    public int deleteOutsidePeriods(String corpCode, List<PeriodKey> keepPeriods) {
        if (keepPeriods.isEmpty()) {
            log.warn("deleteOutsidePeriods called with empty keepPeriods for corp_code={} - no-op", corpCode);
            return 0;
        }
        StringBuilder sql = new StringBuilder(
                "DELETE FROM report_facts WHERE corp_code = ? AND NOT (");
        List<Object> params = new ArrayList<>();
        params.add(corpCode);
        for (int i = 0; i < keepPeriods.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("(bsns_year = ? AND reprt_code = ?)");
            params.add(keepPeriods.get(i).getBsnsYear());
            params.add(keepPeriods.get(i).getReprtCode());
        }
        sql.append(")");
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    /**
     * payload = null stores payload_json as SQL NULL (negative/no-data cache). Caller decides
     * null-ness via Mapper.isPlaceholderOnly.
     *
     * <p>SQL verified column-for-column against db.py:289-312 upsert_report_fact: the INSERT
     * column list does NOT include fetched_at (it relies on the DEFAULT CURRENT_TIMESTAMP on the
     * column, per ddl.sql:534) and the ON DUPLICATE KEY UPDATE clause sets
     * fetched_at = CURRENT_TIMESTAMP (not NOW()) to match the Python source exactly.
     */
    public void upsertReportFact(
            String corpCode, String bsnsYear, String reprtCode, String apiId,
            String rceptNo, List<Map<String, Object>> payload
    ) {
        String payloadJson = payload == null ? null : writePayload(payload);
        jdbcTemplate.update(
                "INSERT INTO report_facts (corp_code, bsns_year, reprt_code, api_id, rcept_no, payload_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE rcept_no = VALUES(rcept_no), payload_json = VALUES(payload_json), "
                        + "fetched_at = CURRENT_TIMESTAMP",
                corpCode, bsnsYear, reprtCode, apiId, rceptNo, payloadJson);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readPayload(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("failed to parse report_facts payload_json, treating as empty: {}", e.getMessage());
            return List.of();
        }
    }

    private String writePayload(List<Map<String, Object>> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize report_facts payload", e);
        }
    }
}
