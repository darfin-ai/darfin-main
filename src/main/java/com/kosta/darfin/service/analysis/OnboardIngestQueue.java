package com.kosta.darfin.service.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 파이프라인 filings가 없는 회사에 초기 backfill job(job_type='onboard_ingest')을
 * 등록한다. dart_pipeline/onboard_ingest.py 참고 — 5년치 정기공시 수집·파싱·
 * 결정론적 개요까지 LLM 없이 한 번에 채운다.
 *
 * 트리거 지점이 하나가 아니라 여러 곳(관심등록, 기업 상세 조회, AI분석 조회)
 * 이어야 자가치유가 된다 — 별표 시점에만 걸어두면, 이 기능이 배포되기 전에
 * 이미 별표된 회사나 배포 타이밍에 걸린 요청은 영원히 filings 없이
 * preview/quant_only에 갇힌다(실제로 SK하이닉스가 이 상태였음). 여러 지점에서
 * 호출해도 pending/running dedup 덕에 중복 job은 안 쌓인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardIngestQueue {

    private final JdbcTemplate jdbcTemplate;

    /**
     * filings가 하나도 없고, 관심 기업(monitored_companies)에 실제로 등록된
     * 회사일 때만 등록한다(이미 pending/running이면 스킵). 후자 조건이
     * 중요하다 — 개요/재무추이 미리보기는 그냥 페이지를 여는 것만으로도
     * companies row가 생기므로(FinancialFactDao/ReportFactDao의
     * ensureCompanyForCache), 그 조건만으로 트리거하면 아무도 관심 없는
     * 회사까지 5년치 딥 백필이 돌아 파이프라인 비용 통제 원칙(§ on-demand만)
     * 을 깬다. 관심등록이 "이 회사는 실제로 필요하다"는 유일한 신뢰 가능한
     * 신호다. companies row가 없는 corp_code는 FK 위반이라 호출 전에 존재를
     * 보장해야 한다.
     */
    public void enqueueIfNeeded(String corpCode) {
        try {
            Integer filingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM filings WHERE corp_code = ?", Integer.class, corpCode);
            if (filingCount != null && filingCount > 0) {
                return;
            }
            Integer starredCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM monitored_companies WHERE corp_code = ?", Integer.class, corpCode);
            if (starredCount == null || starredCount == 0) {
                return;
            }
            List<Long> existing = jdbcTemplate.queryForList(
                    "SELECT id FROM llm_jobs WHERE corp_code = ? AND job_type = 'onboard_ingest' "
                            + "AND status IN ('pending','running')",
                    Long.class, corpCode);
            if (existing.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO llm_jobs (corp_code, job_type) VALUES (?, 'onboard_ingest')", corpCode);
                log.info("onboard_ingest job enqueued for {}", corpCode);
            }
        } catch (Exception e) {
            log.warn("onboard_ingest job enqueue skipped for {}: {}", corpCode, e.getMessage());
        }
    }
}
