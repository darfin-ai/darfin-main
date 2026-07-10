package com.kosta.darfin.service.analysis;

/*
 * Test strategy note: this codebase has no precedent for testing a JdbcTemplate-based class
 * (grep over src/test confirmed no @JdbcTest / embedded-DB usage anywhere), and build.gradle
 * has no H2/testcontainers test dependency — only spring-boot-starter-test. Per the track file's
 * fallback option, adding a new test-scoped DB dependency is out of this track's scope without
 * checking with the user first, so this test mocks JdbcTemplate directly with Mockito and
 * verifies the SQL string + bound params via ArgumentCaptor. This matches the codebase's existing
 * no-Spring-context unit test convention (plain Mockito, no @SpringBootTest/@JdbcTest anywhere
 * else in the tree either).
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReportFactDaoTest {

    private JdbcTemplate jdbcTemplate;
    private ReportFactDao dao;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        dao = new ReportFactDao(jdbcTemplate, new ObjectMapper());
    }

    // ── ensureCompanyForCache ──────────────────────────────────────────

    @Test
    void ensureCompanyForCache_companyAlreadyExists_noInsertAttempted() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) > 0 FROM companies WHERE corp_code = ?"), eq(Boolean.class), eq("00126380")))
                .thenReturn(true);

        dao.ensureCompanyForCache("00126380");

        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void ensureCompanyForCache_companyMissingStockExists_insertHappens() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) > 0 FROM companies WHERE corp_code = ?"), eq(Boolean.class), eq("00126380")))
                .thenReturn(false);
        List<Map<String, Object>> stockRows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("company_name", "삼성전자");
        row.put("stock_code", "005930");
        stockRows.add(row);
        when(jdbcTemplate.queryForList(
                eq("SELECT company_name, stock_code FROM stock WHERE dart_corp_code = ?"), eq("00126380")))
                .thenReturn(stockRows);

        dao.ensureCompanyForCache("00126380");

        verify(jdbcTemplate).update(
                eq("INSERT IGNORE INTO companies (corp_code) VALUES (?)"), eq("00126380"));
    }

    @Test
    void ensureCompanyForCache_companyAndStockMissing_throws() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) > 0 FROM companies WHERE corp_code = ?"), eq(Boolean.class), eq("00126380")))
                .thenReturn(false);
        when(jdbcTemplate.queryForList(
                eq("SELECT company_name, stock_code FROM stock WHERE dart_corp_code = ?"), eq("00126380")))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> dao.ensureCompanyForCache("00126380"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("00126380");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    // ── reportFactsForPeriod ───────────────────────────────────────────

    @Test
    void reportFactsForPeriod_nullPayloadJson_mapsToNullPayload() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> negativeRow = new HashMap<>();
        negativeRow.put("api_id", "alotMatter");
        negativeRow.put("payload_json", null);
        negativeRow.put("rcept_no", "20240101000123");
        negativeRow.put("fetched_at", Timestamp.from(Instant.parse("2024-01-01T00:00:00Z")));
        rows.add(negativeRow);

        Map<String, Object> dataRow = new HashMap<>();
        dataRow.put("api_id", "hyslrSttus");
        dataRow.put("payload_json", "[{\"foo\":\"bar\"}]");
        dataRow.put("rcept_no", "20240101000123");
        dataRow.put("fetched_at", Timestamp.from(Instant.parse("2024-01-02T00:00:00Z")));
        rows.add(dataRow);

        when(jdbcTemplate.queryForList(anyString(), eq("00126380"), eq("2023"), eq("11011")))
                .thenReturn(rows);

        Map<String, ReportFactDao.ReportFactCacheEntry> result =
                dao.reportFactsForPeriod("00126380", "2023", "11011");

        assertThat(result).containsKeys("alotMatter", "hyslrSttus");
        assertThat(result.get("alotMatter").getPayload()).isNull();
        assertThat(result.get("hyslrSttus").getPayload()).isNotNull();
        assertThat(result.get("hyslrSttus").getPayload().get(0)).containsEntry("foo", "bar");
    }

    // ── deleteOutsidePeriods ───────────────────────────────────────────

    @Test
    void deleteOutsidePeriods_emptyKeepPeriods_isNoOp() {
        int deleted = dao.deleteOutsidePeriods("00126380", Collections.emptyList());

        assertThat(deleted).isEqualTo(0);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void deleteOutsidePeriods_nonEmptyList_deletesOnlyRowsOutsideGivenPeriods() {
        // Note: JdbcTemplate#update(String, Object...) is varargs; a single ArgumentCaptor<Object[]>
        // does not reliably capture the whole varargs array across Mockito versions (it can bind to
        // just the first element), so params are matched with individual eq() matchers instead, one
        // per actual bound argument. The sql string is still captured for a full-string assertion.
        when(jdbcTemplate.update(anyString(), any(Object.class), any(Object.class), any(Object.class),
                any(Object.class), any(Object.class))).thenReturn(3);

        List<ReportFactDao.PeriodKey> keep = Arrays.asList(
                new ReportFactDao.PeriodKey("2023", "11011"),
                new ReportFactDao.PeriodKey("2024", "11013"));

        int deleted = dao.deleteOutsidePeriods("00126380", keep);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(),
                eq("00126380"), eq("2023"), eq("11011"), eq("2024"), eq("11013"));

        assertThat(sqlCaptor.getValue())
                .isEqualTo("DELETE FROM report_facts WHERE corp_code = ? AND NOT "
                        + "((bsns_year = ? AND reprt_code = ?) OR (bsns_year = ? AND reprt_code = ?))");
        assertThat(deleted).isEqualTo(3);
    }

    // ── upsertReportFact ───────────────────────────────────────────────

    @Test
    void upsertReportFact_nullPayload_storesSqlNull() {
        // See the note on deleteOutsidePeriods_nonEmptyList... above re: varargs + ArgumentCaptor.
        // Here params are matched with individual eq()/isNull() matchers, one per bound argument.
        dao.upsertReportFact("00126380", "2023", "11011", "alotMatter", "20240101000123", null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(),
                eq("00126380"), eq("2023"), eq("11011"), eq("alotMatter"), eq("20240101000123"), isNull());

        assertThat(sqlCaptor.getValue())
                .isEqualTo("INSERT INTO report_facts (corp_code, bsns_year, reprt_code, api_id, rcept_no, payload_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE rcept_no = VALUES(rcept_no), payload_json = VALUES(payload_json), "
                        + "fetched_at = CURRENT_TIMESTAMP");
    }

    @Test
    void upsertReportFact_nonNullPayload_roundTripsThroughJson() throws Exception {
        List<Map<String, Object>> payload = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("account_nm", "매출액");
        item.put("amount", 12345);
        payload.add(item);

        dao.upsertReportFact("00126380", "2023", "11011", "hyslrSttus", "20240101000123", payload);

        ObjectMapper mapper = new ObjectMapper();
        String expectedJson = mapper.writeValueAsString(payload);

        ArgumentCaptor<String> payloadJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(),
                eq("00126380"), eq("2023"), eq("11011"), eq("hyslrSttus"), eq("20240101000123"),
                payloadJsonCaptor.capture());

        String payloadJson = payloadJsonCaptor.getValue();
        assertThat(payloadJson).isEqualTo(expectedJson);
        assertThat(payloadJson).contains("account_nm").contains("매출액").contains("12345");

        // and it should read back to an equivalent structure
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roundTripped = mapper.readValue(payloadJson, List.class);
        assertThat(roundTripped).hasSize(1);
        assertThat(roundTripped.get(0)).containsEntry("account_nm", "매출액");
    }
}
