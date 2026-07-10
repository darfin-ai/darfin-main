package com.kosta.darfin.service.analysis;

import com.kosta.darfin.dto.analysis.DartOverviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Read-through orchestration for DartOverview: DB cache first, parallel DART fetch only when
 * the current period's rcept_no changed (or cache is missing/negative-cache-expired).
 *
 * Java port of dart_pipeline/dart_overview_rt.py:get_dart_overview() (Track H of the
 * DartOverview Python -&gt; Spring migration). This is a line-by-line behavioral port, not a
 * reimplementation from a high-level summary — see the migration plan's "Risky areas" section
 * for the specific conditionals that must be preserved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartOverviewService {

    private static final int MAX_FALLBACK_CANDIDATES = ReportFactApiIds.MAX_FALLBACK_CANDIDATES;

    private final ReportFactDao reportFactDao;
    private final DartApiClient dartApiClient;
    private final PeriodResolver periodResolver;
    private final FreshnessPolicy freshnessPolicy;
    private final Mapper mapper;
    private final Composer composer;
    private final ExecutorService dartOverviewExecutor;

    /**
     * @return the composed DartOverview, or null if the read-through fails entirely
     *         (mirrors the previous DartOverviewClient's fail-soft contract — a DART/DB
     *         outage on this integration should not fail the whole company-detail response).
     */
    public DartOverviewResponse getDartOverview(String corpCode, boolean force) {
        try {
            return doGetDartOverview(corpCode, force);
        } catch (Exception e) {
            log.warn("dartOverview failed for {}: {}", corpCode, e.getMessage(), e);
            return null;
        }
    }

    private DartOverviewResponse doGetDartOverview(String corpCode, boolean force) {
        // 1. FK bootstrap — runs first, every request, even for browse-only stocks.
        reportFactDao.ensureCompanyForCache(corpCode);

        // 2. Resolve the current reporting period via a live DART list.json call.
        PeriodResolution period = resolvePeriod(corpCode);
        String bsnsYear = period.bsnsYear;
        String reprtCode = period.reprtCode;
        String rceptNo = period.rceptNo;
        List<PeriodResolver.PeriodCandidate> candidates = period.candidates;

        // 3. Prune report_facts rows outside the lookback window — only when the period was
        // actually verified via DART (rceptNo != null). Pruning on an unverified fallback guess
        // would wrongly delete good cache.
        if (rceptNo != null) {
            List<ReportFactDao.PeriodKey> keepPeriods = new ArrayList<>();
            for (PeriodResolver.PeriodCandidate c : candidates) {
                keepPeriods.add(new ReportFactDao.PeriodKey(c.getBsnsYear(), c.getReprtCode()));
            }
            int deleted = reportFactDao.deleteOutsidePeriods(corpCode, keepPeriods);
            if (deleted > 0) {
                log.info("pruned {} stale report_facts row(s) for {} (keeping {}/{})",
                        deleted, corpCode, bsnsYear, reprtCode);
            }
        }

        // 4. Determine stale api_ids and refresh them (concurrently), tolerating DART quota
        // exhaustion by serving whatever's cached instead of failing the request.
        Map<String, ReportFactDao.ReportFactCacheEntry> existing =
                reportFactDao.reportFactsForPeriod(corpCode, bsnsYear, reprtCode);
        Map<String, FreshnessPolicy.ExistingFact> existingFacts = new HashMap<>();
        for (Map.Entry<String, ReportFactDao.ReportFactCacheEntry> e : existing.entrySet()) {
            ReportFactDao.ReportFactCacheEntry entry = e.getValue();
            existingFacts.put(e.getKey(), new FreshnessPolicy.ExistingFact(
                    entry.getRceptNo(), entry.getPayload() == null, entry.getFetchedAt()));
        }
        List<String> staleIds = freshnessPolicy.staleApiIds(
                existingFacts, ReportFactApiIds.ALL, rceptNo, Instant.now(), force);

        if (!staleIds.isEmpty()) {
            refreshStale(corpCode, bsnsYear, reprtCode, rceptNo, staleIds);
            // quota-exceeded is handled inside refreshStale (logged, not thrown) — the request
            // continues and serves whatever ended up cached, matching Python's
            // "except QuotaExceededError: pass # serve partial cache".
        }

        // 5. Reload from cache and apply the self-healing placeholder re-filter on every read —
        // catches rows that were cached as "real data" under an older/buggy placeholder
        // classification, without needing a backfill job.
        Map<String, ReportFactDao.ReportFactCacheEntry> reloaded =
                reportFactDao.reportFactsForPeriod(corpCode, bsnsYear, reprtCode);
        Map<String, List<Map<String, Object>>> payloads = new HashMap<>();
        for (String apiId : ReportFactApiIds.ALL) {
            ReportFactDao.ReportFactCacheEntry entry = reloaded.get(apiId);
            List<Map<String, Object>> payload = entry == null ? null : entry.getPayload();
            payloads.put(apiId, mapper.isPlaceholderOnly(payload) ? null : payload);
        }

        // 6. Period-fallback for sections still missing after refresh — bounded to the 3 most
        // recent older candidates, excluding alotMatter (has its own embedded 3-year history).
        Map<String, Composer.FallbackInfo> fallbackInfo = new HashMap<>();
        List<String> missingApiIds = new ArrayList<>();
        for (String apiId : ReportFactApiIds.FALLBACK_ELIGIBLE) {
            if (payloads.get(apiId) == null) {
                missingApiIds.add(apiId);
            }
        }
        if (!missingApiIds.isEmpty() && candidates.size() > 1) {
            List<PeriodResolver.PeriodCandidate> olderCandidates =
                    candidates.subList(1, candidates.size());
            Map<String, FallbackResolved> resolved =
                    resolveSectionsWithFallback(corpCode, missingApiIds, olderCandidates);
            for (Map.Entry<String, FallbackResolved> e : resolved.entrySet()) {
                payloads.put(e.getKey(), e.getValue().payload);
                fallbackInfo.put(e.getKey(), e.getValue().asOf);
            }
        }

        // 7. Compose the final response.
        return composer.compose(bsnsYear, reprtCode, rceptNo, payloads, fallbackInfo);
    }

    // ── Period resolution ───────────────────────────────────────────────

    private static class PeriodResolution {
        final String bsnsYear;
        final String reprtCode;
        final String rceptNo; // null if list.json failed / period unverified
        final List<PeriodResolver.PeriodCandidate> candidates;

        PeriodResolution(String bsnsYear, String reprtCode, String rceptNo,
                          List<PeriodResolver.PeriodCandidate> candidates) {
            this.bsnsYear = bsnsYear;
            this.reprtCode = reprtCode;
            this.rceptNo = rceptNo;
            this.candidates = candidates;
        }
    }

    private PeriodResolution resolvePeriod(String corpCode) {
        PeriodResolver.DateRange range = periodResolver.listFilingsDateRange(LocalDate.now());
        List<Map<String, Object>> items;
        try {
            items = dartApiClient.listFilings(corpCode, range.getBgnDe(), range.getEndDe());
        } catch (DartApiClient.DartApiException | RestClientException e) {
            log.warn("list.json failed for {}: {}", corpCode, e.getMessage());
            items = List.of();
        }

        List<PeriodResolver.PeriodCandidate> candidates = periodResolver.periodicCandidatesFromList(items);
        if (!candidates.isEmpty()) {
            PeriodResolver.PeriodCandidate latest = candidates.get(0);
            return new PeriodResolution(latest.getBsnsYear(), latest.getReprtCode(), latest.getRceptNo(), candidates);
        }

        int fallbackYear = LocalDate.now().getYear() - 1;
        log.warn("no periodic filing in list.json for {} ({}~{}), falling back to {} 11011",
                corpCode, range.getBgnDe(), range.getEndDe(), fallbackYear);
        return new PeriodResolution(String.valueOf(fallbackYear), "11011", null, List.of());
    }

    // ── Concurrent refresh ──────────────────────────────────────────────

    private static class FetchOutcome {
        final String apiId;
        final List<Map<String, Object>> rows; // null on 013 (no data)
        final DartApiClient.DartApiException dartError; // non-null on DART-level error
        final RuntimeException otherError; // non-null on any other failure

        FetchOutcome(String apiId, List<Map<String, Object>> rows,
                     DartApiClient.DartApiException dartError, RuntimeException otherError) {
            this.apiId = apiId;
            this.rows = rows;
            this.dartError = dartError;
            this.otherError = otherError;
        }
    }

    private FetchOutcome fetchOne(String apiId, String corpCode, String bsnsYear, String reprtCode) {
        try {
            List<Map<String, Object>> rows = dartApiClient.reportApi(apiId, corpCode, bsnsYear, reprtCode);
            return new FetchOutcome(apiId, rows, null, null);
        } catch (DartApiClient.DartApiException e) {
            return new FetchOutcome(apiId, null, e, null);
        } catch (RuntimeException e) {
            return new FetchOutcome(apiId, null, null, e);
        }
    }

    /**
     * Fetches apiIds concurrently and upserts successful results, applying placeholder
     * classification at write time. Quota exhaustion (020) is logged, not thrown — the caller
     * continues and serves whatever ended up cached, matching Python's graceful degradation.
     */
    private boolean refreshStale(
            String corpCode, String bsnsYear, String reprtCode, String rceptNo, List<String> apiIds
    ) {
        List<CompletableFuture<FetchOutcome>> futures = new ArrayList<>();
        for (String apiId : apiIds) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchOne(apiId, corpCode, bsnsYear, reprtCode), dartOverviewExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        boolean quotaHit = false;
        for (CompletableFuture<FetchOutcome> future : futures) {
            FetchOutcome outcome = future.join();
            if (outcome.dartError != null) {
                if (outcome.dartError.isQuotaExceeded()) {
                    quotaHit = true;
                    log.warn("DART quota exceeded for {}/{}", corpCode, outcome.apiId);
                } else {
                    log.warn("DART error for {}/{}: {}", corpCode, outcome.apiId, outcome.dartError.getMessage());
                }
                continue;
            }
            if (outcome.otherError != null) {
                log.warn("fetch failed for {}: {}", outcome.apiId, outcome.otherError.getMessage());
                continue;
            }
            boolean placeholder = mapper.isPlaceholderOnly(outcome.rows);
            reportFactDao.upsertReportFact(
                    corpCode, bsnsYear, reprtCode, outcome.apiId, rceptNo,
                    placeholder ? null : outcome.rows);
        }
        return quotaHit;
    }

    // ── Period fallback ─────────────────────────────────────────────────

    private static class FallbackResolved {
        final List<Map<String, Object>> payload;
        final Composer.FallbackInfo asOf;

        FallbackResolved(List<Map<String, Object>> payload, Composer.FallbackInfo asOf) {
            this.payload = payload;
            this.asOf = asOf;
        }
    }

    /**
     * Backfills missing sections from up to MAX_FALLBACK_CANDIDATES older periodic filings,
     * most recent first. A section counts as "already covered" by an older period as soon as a
     * report_facts row exists for it there (even a negative-cache row) — it is not re-fetched,
     * mirroring Python's `if a not in cached` check (presence, not payload truthiness).
     */
    private Map<String, FallbackResolved> resolveSectionsWithFallback(
            String corpCode, List<String> missingApiIds, List<PeriodResolver.PeriodCandidate> olderCandidates
    ) {
        Map<String, FallbackResolved> resolved = new HashMap<>();
        Set<String> remaining = new LinkedHashSet<>(missingApiIds);
        boolean quotaHit = false;

        int candidatesTried = 0;
        for (PeriodResolver.PeriodCandidate candidate : olderCandidates) {
            if (candidatesTried >= MAX_FALLBACK_CANDIDATES) break;
            candidatesTried++;
            if (remaining.isEmpty() || quotaHit) break;

            String bsnsYear = candidate.getBsnsYear();
            String reprtCode = candidate.getReprtCode();
            String candRceptNo = candidate.getRceptNo();

            Map<String, ReportFactDao.ReportFactCacheEntry> cached =
                    reportFactDao.reportFactsForPeriod(corpCode, bsnsYear, reprtCode);

            List<String> stillMissing = new ArrayList<>();
            Map<String, List<Map<String, Object>>> results = new HashMap<>();
            for (String apiId : remaining) {
                if (cached.containsKey(apiId)) {
                    results.put(apiId, cached.get(apiId).getPayload());
                } else {
                    stillMissing.add(apiId);
                }
            }

            if (!stillMissing.isEmpty()) {
                List<CompletableFuture<FetchOutcome>> futures = new ArrayList<>();
                for (String apiId : stillMissing) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> fetchOne(apiId, corpCode, bsnsYear, reprtCode), dartOverviewExecutor));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                for (CompletableFuture<FetchOutcome> future : futures) {
                    FetchOutcome outcome = future.join();
                    if (outcome.dartError != null) {
                        if (outcome.dartError.isQuotaExceeded()) {
                            quotaHit = true;
                            log.warn("DART quota exceeded during fallback for {}/{}", corpCode, outcome.apiId);
                        } else {
                            log.warn("DART error during fallback for {}/{}: {}",
                                    corpCode, outcome.apiId, outcome.dartError.getMessage());
                        }
                        continue;
                    }
                    if (outcome.otherError != null) {
                        log.warn("fallback fetch failed for {}: {}", outcome.apiId, outcome.otherError.getMessage());
                        continue;
                    }
                    boolean placeholder = mapper.isPlaceholderOnly(outcome.rows);
                    reportFactDao.upsertReportFact(
                            corpCode, bsnsYear, reprtCode, outcome.apiId, candRceptNo,
                            placeholder ? null : outcome.rows);
                    results.put(outcome.apiId, outcome.rows);
                }
            }

            for (Map.Entry<String, List<Map<String, Object>>> e : results.entrySet()) {
                String apiId = e.getKey();
                List<Map<String, Object>> payload = e.getValue();
                if (payload == null || mapper.isPlaceholderOnly(payload)) {
                    continue;
                }
                resolved.put(apiId, new FallbackResolved(
                        payload, new Composer.FallbackInfo(bsnsYear, reprtCode, candRceptNo)));
                remaining.remove(apiId);
            }
        }

        return resolved;
    }
}
