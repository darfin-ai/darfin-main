package com.kosta.darfin.service.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class FreshnessPolicy {

    /** One existing report_facts row, as read by ReportFactDao — payloadIsNull mirrors `payload_json IS NULL`. */
    @Getter
    @RequiredArgsConstructor
    public static class ExistingFact {
        private final String rceptNo;
        private final boolean payloadIsNull;
        private final Instant fetchedAt;
    }

    /**
     * @param existingByApiId apiId -> the currently cached row for this (corpCode, bsnsYear, reprtCode), or absent if no row exists
     * @param requestedApiIds the api_ids the caller cares about (normally ReportFactApiIds.ALL)
     * @param currentRceptNo   the just-resolved period's rcept_no; null if the period couldn't be verified via DART list.json this request
     * @param now              injected clock value, for testability — do not call Instant.now() internally
     * @param force            bypass all rules, return every requested id as stale
     */
    public List<String> staleApiIds(
            Map<String, ExistingFact> existingByApiId,
            List<String> requestedApiIds,
            String currentRceptNo,
            Instant now,
            boolean force
    ) {
        if (force) {
            return List.copyOf(requestedApiIds);
        }
        Duration retryWindow = Duration.ofHours(ReportFactApiIds.NEGATIVE_RETRY_HOURS);
        return requestedApiIds.stream()
                .filter(apiId -> isStale(existingByApiId.get(apiId), currentRceptNo, now, retryWindow))
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean isStale(ExistingFact fact, String currentRceptNo, Instant now, Duration retryWindow) {
        if (fact == null) {
            return true; // rule 1: missing row -> stale
        }
        if (currentRceptNo == null) {
            return false; // rule 2: period unverified -> treat existing row as fresh
        }
        if (!currentRceptNo.equals(fact.getRceptNo())) {
            return true; // rule 3: rcept_no changed -> stale
        }
        boolean pastRetryWindow = fact.getFetchedAt().isBefore(now.minus(retryWindow));
        return fact.isPayloadIsNull() && pastRetryWindow; // rule 4, else rule 5 (fresh)
    }
}
