package com.kosta.darfin.service.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Read-through orchestration for financial trends ("재무 추이"): DB cache
 * (financial_facts) first, live DART fetch only when a period's data is stale/missing.
 * Same shape as DartOverviewService (Track H's on-demand pattern) but simpler — every
 * periodic filing within the lookback window is fetched explicitly, there's no
 * "current period + fallback candidates" scheme, since a trend chart needs the whole
 * series rather than just the latest snapshot.
 *
 * Sole source for the 재무 추이 API since 2026-07-13 (the Python-owned `metrics` table
 * and its batch merge in CompanyAnalysisService were retired). The Python pipeline still
 * writes the same financial_facts table (report_facts-style dual-writer convention) via
 * financial_facts_ingest.py, both to pre-warm diff inputs and to backfill periods older
 * than this service's lookback — which is why serving reads ALL cached periods, not just
 * the refresh-window candidates.
 *
 * Lookback is ~2 years (see LOOKBACK_DAYS) — wider than DartOverviewService's 548-day
 * default since financial history benefits from more depth, but bounded to keep
 * worst-case cold-start latency reasonable under DartApiClient's 0.3s/call throttle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialFactsService {

    private static final int LOOKBACK_DAYS = 730; // ~2 years
    private static final List<String> FS_DIVS = List.of("CFS", "OFS");

    private final FinancialFactDao financialFactDao;
    private final ReportFactDao reportFactDao; // reused only for ensureCompanyForCache
    private final DartApiClient dartApiClient;
    private final PeriodResolver periodResolver;
    private final FreshnessPolicy freshnessPolicy;
    private final ExecutorService dartOverviewExecutor;

    /**
     * Account rows (concept/account_nm/statement_type/ord/amount/bsns_year/reprt_code/
     * period_qualifier/is_consolidated/id) for every cached period, after refreshing
     * stale/missing periods within the lookback window from DART.
     *
     * @return empty list if the read-through fails entirely (fail-soft — a DART/DB outage
     *         on this integration should not fail the whole company-detail response).
     */
    public List<Map<String, Object>> liveMetricRows(String corpCode) {
        return liveMetricRows(corpCode, LOOKBACK_DAYS);
    }

    /**
     * Overload with a caller-supplied lookback. AI분석(RiskAnalysisService)이 상태머신
     * 이력용으로 ~5년을 요구한다 — 재무추이 탭은 기본 730일을 유지하고, 캐시 테이블은
     * 공유하므로(서빙은 어차피 전체 캐시를 읽는다) 더 넓은 창은 콜드 기업 첫 조회 시
     * 추가 DART 호출 비용만 만든다.
     */
    public List<Map<String, Object>> liveMetricRows(String corpCode, int lookbackDays) {
        try {
            return doLiveMetricRows(corpCode, lookbackDays);
        } catch (Exception e) {
            log.warn("financial facts read-through failed for {}: {}", corpCode, e.getMessage(), e);
            return List.of();
        }
    }

    private List<Map<String, Object>> doLiveMetricRows(String corpCode, int lookbackDays) {
        reportFactDao.ensureCompanyForCache(corpCode);

        PeriodResolver.DateRange range = periodResolver.listFilingsDateRange(LocalDate.now(), lookbackDays);
        List<Map<String, Object>> items;
        try {
            items = dartApiClient.listFilings(corpCode, range.getBgnDe(), range.getEndDe());
        } catch (DartApiClient.DartApiException | RestClientException e) {
            log.warn("list.json failed for {}: {}", corpCode, e.getMessage());
            items = List.of();
        }
        List<PeriodResolver.PeriodCandidate> candidates = periodResolver.periodicCandidatesFromList(items);
        for (PeriodResolver.PeriodCandidate candidate : candidates) {
            refreshPeriodIfStale(corpCode, candidate);
        }

        // Serve everything cached, not just the refreshed candidates — the Python daily
        // scan warms periods beyond the lookback window (deep chart history).
        List<Map<String, Object>> rows = new ArrayList<>();
        long syntheticId = 0;
        for (FinancialFactDao.FinancialFactRow fact : financialFactDao.allFinancialFacts(corpCode)) {
            if (fact.getPayload() == null || fact.getPayload().isEmpty()) {
                continue; // negative cache or not yet fetched (e.g. quota exhausted this request)
            }
            boolean isConsolidated = "CFS".equals(fact.getFsDiv());
            List<Map<String, Object>> transformed = FinancialFactTransformer.transform(
                    fact.getPayload(), fact.getRceptNo(), corpCode,
                    fact.getBsnsYear(), fact.getReprtCode(), isConsolidated);
            for (Map<String, Object> row : transformed) {
                row.put("id", syntheticId++);
                rows.add(row);
            }
        }
        return rows;
    }

    private void refreshPeriodIfStale(String corpCode, PeriodResolver.PeriodCandidate candidate) {
        String bsnsYear = candidate.getBsnsYear();
        String reprtCode = candidate.getReprtCode();
        String rceptNo = candidate.getRceptNo();

        Map<String, FinancialFactDao.FinancialFactCacheEntry> existing =
                financialFactDao.financialFactsForPeriod(corpCode, bsnsYear, reprtCode);
        Map<String, FreshnessPolicy.ExistingFact> existingFacts = new HashMap<>();
        for (Map.Entry<String, FinancialFactDao.FinancialFactCacheEntry> e : existing.entrySet()) {
            FinancialFactDao.FinancialFactCacheEntry entry = e.getValue();
            existingFacts.put(e.getKey(), new FreshnessPolicy.ExistingFact(
                    entry.getRceptNo(), entry.getPayload() == null, entry.getFetchedAt()));
        }
        List<String> staleFsDivs = freshnessPolicy.staleApiIds(existingFacts, FS_DIVS, rceptNo, Instant.now(), false);
        if (staleFsDivs.isEmpty()) {
            return;
        }

        List<CompletableFuture<FetchOutcome>> futures = new ArrayList<>();
        for (String fsDiv : staleFsDivs) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchOne(corpCode, bsnsYear, reprtCode, fsDiv), dartOverviewExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<FetchOutcome> future : futures) {
            FetchOutcome outcome = future.join();
            if (outcome.dartError != null) {
                if (outcome.dartError.isQuotaExceeded()) {
                    log.warn("DART quota exceeded for {}/{}/{}", corpCode, bsnsYear, outcome.fsDiv);
                } else {
                    log.warn("DART error for {}/{}/{}: {}", corpCode, bsnsYear, outcome.fsDiv, outcome.dartError.getMessage());
                }
                continue;
            }
            if (outcome.otherError != null) {
                log.warn("fetch failed for {}/{}: {}", corpCode, outcome.fsDiv, outcome.otherError.getMessage());
                continue;
            }
            List<Map<String, Object>> payload = outcome.rows.isEmpty() ? null : outcome.rows;
            financialFactDao.upsertFinancialFact(corpCode, bsnsYear, reprtCode, outcome.fsDiv, rceptNo, payload);
        }
    }

    private static class FetchOutcome {
        final String fsDiv;
        final List<Map<String, Object>> rows; // null on error
        final DartApiClient.DartApiException dartError;
        final RuntimeException otherError;

        FetchOutcome(String fsDiv, List<Map<String, Object>> rows,
                     DartApiClient.DartApiException dartError, RuntimeException otherError) {
            this.fsDiv = fsDiv;
            this.rows = rows;
            this.dartError = dartError;
            this.otherError = otherError;
        }
    }

    private FetchOutcome fetchOne(String corpCode, String bsnsYear, String reprtCode, String fsDiv) {
        try {
            List<Map<String, Object>> rows = dartApiClient.fnlttSinglAcntAll(corpCode, bsnsYear, reprtCode, fsDiv);
            return new FetchOutcome(fsDiv, rows, null, null);
        } catch (DartApiClient.DartApiException e) {
            return new FetchOutcome(fsDiv, null, e, null);
        } catch (RuntimeException e) {
            return new FetchOutcome(fsDiv, null, null, e);
        }
    }
}
