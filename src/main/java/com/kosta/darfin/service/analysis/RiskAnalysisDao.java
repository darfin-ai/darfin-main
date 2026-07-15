package com.kosta.darfin.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.sql.Timestamp;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI분석 테이블(derived_metrics/risk_states/dossier_events) 접근 계층.
 * FinancialFactDao와 같은 이유로 JPA 엔티티 없이 JdbcTemplate만 쓴다 —
 * Python 파이프라인(Layer 2)이 같은 테이블의 text_signals/narrative 컬럼을
 * 쓰기 때문에 Hibernate가 스키마를 건드리면 안 된다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RiskAnalysisDao {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // ── derived_metrics ──────────────────────────────────────────────────

    /** 저장된 최신 분기의 rcept_no — 재계산 필요 여부(staleness) 판정용. null = 캐시 없음. */
    public String latestComputedRceptNo(String corpCode, String fsDiv) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT rcept_no FROM derived_metrics WHERE corp_code = ? AND fs_div = ? "
                        + "ORDER BY quarter DESC LIMIT 1",
                String.class, corpCode, fsDiv);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Getter
    @RequiredArgsConstructor
    public static class DerivedMetricsSnapshot {
        private final String quarter;
        private final String rceptNo;
        private final Map<String, Object> metrics;
    }

    /** quarter → 저장된 스냅샷 — 정정공시로 인한 수치 변경(correction_material) 감지용. */
    @SuppressWarnings("unchecked")
    public Map<String, DerivedMetricsSnapshot> metricsSnapshots(String corpCode, String fsDiv) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT quarter, rcept_no, metrics_json FROM derived_metrics "
                        + "WHERE corp_code = ? AND fs_div = ?",
                corpCode, fsDiv);
        Map<String, DerivedMetricsSnapshot> out = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            String quarter = (String) row.get("quarter");
            Object metrics = readJson((String) row.get("metrics_json"));
            out.put(quarter, new DerivedMetricsSnapshot(
                    quarter, (String) row.get("rcept_no"),
                    metrics instanceof Map ? (Map<String, Object>) metrics : Map.of()));
        }
        return out;
    }

    public void upsertDerivedMetrics(String corpCode, String quarter, String fsDiv,
                                     String rceptNo, Map<String, Object> metrics) {
        jdbcTemplate.update(
                "INSERT INTO derived_metrics (corp_code, quarter, fs_div, rcept_no, metrics_json) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE rcept_no = VALUES(rcept_no), "
                        + "metrics_json = VALUES(metrics_json), computed_at = CURRENT_TIMESTAMP",
                corpCode, quarter, fsDiv, rceptNo, writeJson(metrics));
    }

    // ── risk_states ──────────────────────────────────────────────────────

    /**
     * quant 필드만 갱신 — Python(Layer 2)이 채운 text_signals/narrative/watch_next는
     * 보존한다. 상태가 바뀐 분기는 narrative가 낡을 수 있지만 llm_updated_at <
     * computed_at으로 프론트/워커가 감지할 수 있다.
     */
    public void upsertRiskState(String corpCode, String quarter, String category,
                                String state, int consecutiveQtrs, Map<String, Object> quantSignals) {
        jdbcTemplate.update(
                "INSERT INTO risk_states (corp_code, quarter, category, state, consecutive_qtrs, quant_signals_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE state = VALUES(state), "
                        + "consecutive_qtrs = VALUES(consecutive_qtrs), "
                        + "quant_signals_json = VALUES(quant_signals_json), computed_at = CURRENT_TIMESTAMP",
                corpCode, quarter, category, state, consecutiveQtrs, writeJson(quantSignals));
    }

    @Getter
    @RequiredArgsConstructor
    public static class RiskStateRow {
        private final String quarter;
        private final String category;
        private final String state;
        private final int consecutiveQtrs;
        private final Object quantSignals;
        private final Object textSignals;
        private final String narrativeKo;
        private final String watchNextKo;
        private final Timestamp computedAt;
        private final Timestamp llmUpdatedAt;

        public boolean isNarrativeFresh() {
            boolean watchComplete = RiskStateMachine.NORMAL.equals(state)
                    || RiskStateMachine.RESOLVED.equals(state)
                    || RiskStateMachine.INSUFFICIENT.equals(state)
                    || (watchNextKo != null && !watchNextKo.trim().isEmpty());
            return narrativeKo != null && !narrativeKo.trim().isEmpty()
                    && watchComplete
                    && computedAt != null && llmUpdatedAt != null
                    && !llmUpdatedAt.before(computedAt);
        }
    }

    /** 전 분기 × 전 카테고리, 분기 오름차순. */
    public List<RiskStateRow> riskStates(String corpCode) {
        return jdbcTemplate.query(
                "SELECT quarter, category, state, consecutive_qtrs, quant_signals_json, "
                        + "text_signals_json, narrative_ko, watch_next_ko, computed_at, llm_updated_at "
                        + "FROM risk_states WHERE corp_code = ? ORDER BY quarter, category",
                (rs, i) -> new RiskStateRow(
                        rs.getString("quarter"),
                        rs.getString("category"),
                        rs.getString("state"),
                        rs.getInt("consecutive_qtrs"),
                        readJson(rs.getString("quant_signals_json")),
                        readJson(rs.getString("text_signals_json")),
                        rs.getString("narrative_ko"),
                        rs.getString("watch_next_ko"),
                        rs.getTimestamp("computed_at"),
                        rs.getTimestamp("llm_updated_at")),
                corpCode);
    }

    // ── dossier_events ───────────────────────────────────────────────────

    /** UNIQUE(uq_dossier_event) 충돌 시 무시 — 재계산 멱등성. */
    public void insertDossierEventIfAbsent(String corpCode, String rceptNo, String eventType,
                                           String category, String itemKey, Map<String, Object> detail) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO dossier_events (corp_code, rcept_no, event_type, category, item_key, detail_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                corpCode, rceptNo, eventType, category, itemKey, writeJson(detail));
    }

    @Getter
    @RequiredArgsConstructor
    public static class DossierEventRow {
        private final String rceptNo;
        private final String eventType;
        private final String category;
        private final String itemKey;
        private final Object detail;
        private final String createdAt;
    }

    public List<DossierEventRow> dossierEvents(String corpCode) {
        return jdbcTemplate.query(
                "SELECT rcept_no, event_type, category, item_key, detail_json, created_at "
                        + "FROM dossier_events WHERE corp_code = ? ORDER BY created_at DESC, id DESC LIMIT 100",
                (rs, i) -> new DossierEventRow(
                        rs.getString("rcept_no"),
                        rs.getString("event_type"),
                        rs.getString("category"),
                        rs.getString("item_key"),
                        readJson(rs.getString("detail_json")),
                        String.valueOf(rs.getTimestamp("created_at"))),
                corpCode);
    }

    // ── llm_jobs (risk_analysis) ─────────────────────────────────────────

    @Getter
    @RequiredArgsConstructor
    public static class RiskJobStatus {
        private final String status;
        private final String errorCode;
        private final boolean retryable;
    }

    public RiskJobStatus latestRiskJobStatus(String corpCode) {
        List<RiskJobStatus> rows = jdbcTemplate.query(
                "SELECT status, error_message FROM llm_jobs WHERE corp_code = ? "
                        + "AND job_type = 'risk_analysis' ORDER BY requested_at DESC, id DESC LIMIT 1",
                (rs, i) -> {
                    String status = rs.getString("status");
                    String error = rs.getString("error_message");
                    return new RiskJobStatus(status, safeErrorCode(error), "failed".equals(status));
                }, corpCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Company row lock serializes concurrent enqueue attempts without a schema migration. */
    @Transactional
    public void enqueueRiskJob(String corpCode) {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT corp_code FROM companies WHERE corp_code = ? FOR UPDATE",
                    String.class, corpCode);
            List<Long> existing = jdbcTemplate.queryForList(
                    "SELECT id FROM llm_jobs WHERE corp_code = ? AND job_type = 'risk_analysis' "
                            + "AND status IN ('pending','running')",
                    Long.class, corpCode);
            if (existing.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO llm_jobs (corp_code, job_type) VALUES (?, 'risk_analysis')", corpCode);
            }
        } catch (Exception e) {
            log.warn("risk_analysis job enqueue skipped for {}: {}", corpCode, e.getMessage());
        }
    }

    private String safeErrorCode(String error) {
        if (error == null || error.trim().isEmpty()) return null;
        String normalized = error.toLowerCase();
        if (normalized.contains("timeout") || normalized.contains("deadline")) return "AI_TIMEOUT";
        if (normalized.contains("429") || normalized.contains("quota") || normalized.contains("rate")) return "AI_RATE_LIMITED";
        if (normalized.contains("dart")) return "SOURCE_UNAVAILABLE";
        return "AI_PROCESSING_FAILED";
    }

    // ── JSON helpers ─────────────────────────────────────────────────────

    private Object readJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("risk analysis JSON 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("risk analysis JSON 직렬화 실패", e);
        }
    }
}
