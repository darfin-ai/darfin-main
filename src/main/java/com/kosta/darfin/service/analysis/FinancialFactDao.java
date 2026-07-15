package com.kosta.darfin.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * financial_facts 테이블 접근 계층. ReportFactDao와 동일한 read-through 캐시 패턴이지만
 * key의 마지막 축이 api_id 대신 fs_div(CFS/OFS)다. FK bootstrap(companies row 생성)은
 * ReportFactDao.ensureCompanyForCache()를 그대로 재사용한다 — 두 캐시 모두 같은
 * companies(corp_code) FK를 참조하므로 중복 구현할 필요가 없다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FinancialFactDao {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Getter
    @RequiredArgsConstructor
    public static class FinancialFactCacheEntry {
        private final String fsDiv;
        private final String rceptNo;
        private final List<Map<String, Object>> payload;
        private final Instant fetchedAt;
    }

    /** fs_div -> cached row, for the given period. payload is null when payload_json IS NULL (negative cache). */
    public Map<String, FinancialFactCacheEntry> financialFactsForPeriod(String corpCode, String bsnsYear, String reprtCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT fs_div, payload_json, rcept_no, fetched_at FROM financial_facts "
                        + "WHERE corp_code = ? AND bsns_year = ? AND reprt_code = ?",
                corpCode, bsnsYear, reprtCode);
        Map<String, FinancialFactCacheEntry> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String fsDiv = (String) row.get("fs_div");
            String payloadJson = (String) row.get("payload_json");
            List<Map<String, Object>> payload = payloadJson == null ? null : readPayload(payloadJson);
            java.sql.Timestamp fetchedAt = (java.sql.Timestamp) row.get("fetched_at");
            out.put(fsDiv, new FinancialFactCacheEntry(
                    fsDiv, (String) row.get("rcept_no"), payload, fetchedAt.toInstant()));
        }
        return out;
    }

    @Getter
    @RequiredArgsConstructor
    public static class FinancialFactRow {
        private final String bsnsYear;
        private final String reprtCode;
        private final String fsDiv;
        private final String rceptNo;
        private final List<Map<String, Object>> payload; // null = negative cache
    }

    /**
     * Every cached period for the company, oldest first. Serving reads this (not just the
     * refresh-window candidates) so periods warmed by the Python daily scan / onboarding —
     * which can reach further back than the read-through's lookback — stay on the chart.
     */
    public List<FinancialFactRow> allFinancialFacts(String corpCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT bsns_year, reprt_code, fs_div, rcept_no, payload_json FROM financial_facts "
                        + "WHERE corp_code = ? ORDER BY bsns_year, reprt_code",
                corpCode);
        List<FinancialFactRow> out = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String payloadJson = (String) row.get("payload_json");
            out.add(new FinancialFactRow(
                    (String) row.get("bsns_year"),
                    (String) row.get("reprt_code"),
                    (String) row.get("fs_div"),
                    (String) row.get("rcept_no"),
                    payloadJson == null ? null : readPayload(payloadJson)));
        }
        return out;
    }

    /** payload = null stores payload_json as SQL NULL (negative/no-data cache). */
    public void upsertFinancialFact(
            String corpCode, String bsnsYear, String reprtCode, String fsDiv,
            String rceptNo, List<Map<String, Object>> payload
    ) {
        String payloadJson = payload == null ? null : writePayload(payload);
        jdbcTemplate.update(
                "INSERT INTO financial_facts (corp_code, bsns_year, reprt_code, fs_div, rcept_no, payload_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE rcept_no = VALUES(rcept_no), payload_json = VALUES(payload_json), "
                        + "fetched_at = CURRENT_TIMESTAMP",
                corpCode, bsnsYear, reprtCode, fsDiv, rceptNo, payloadJson);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readPayload(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("failed to parse financial_facts payload_json, treating as empty: {}", e.getMessage());
            return List.of();
        }
    }

    private String writePayload(List<Map<String, Object>> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize financial_facts payload", e);
        }
    }
}
