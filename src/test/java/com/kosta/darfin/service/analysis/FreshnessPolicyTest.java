package com.kosta.darfin.service.analysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
    private final FreshnessPolicy policy = new FreshnessPolicy();

    @Test
    void missingRowIsStale() {
        List<String> stale = policy.staleApiIds(Map.of(), List.of("alotMatter"), "R1", NOW, false);
        assertThat(stale).containsExactly("alotMatter");
    }

    @Test
    void unverifiedPeriodTreatsExistingRowAsFresh() {
        FreshnessPolicy.ExistingFact fact = new FreshnessPolicy.ExistingFact("R1", true, NOW.minusSeconds(999_999));
        List<String> stale = policy.staleApiIds(
                Map.of("alotMatter", fact), List.of("alotMatter"), null, NOW, false);
        assertThat(stale).isEmpty();
    }

    @Test
    void changedRceptNoIsStale() {
        FreshnessPolicy.ExistingFact fact = new FreshnessPolicy.ExistingFact("OLD", false, NOW.minusSeconds(60));
        List<String> stale = policy.staleApiIds(
                Map.of("alotMatter", fact), List.of("alotMatter"), "NEW", NOW, false);
        assertThat(stale).containsExactly("alotMatter");
    }

    @Test
    void negativeCachePastRetryWindowIsStale() {
        FreshnessPolicy.ExistingFact fact = new FreshnessPolicy.ExistingFact(
                "R1", true, NOW.minus(java.time.Duration.ofHours(25)));
        List<String> stale = policy.staleApiIds(
                Map.of("alotMatter", fact), List.of("alotMatter"), "R1", NOW, false);
        assertThat(stale).containsExactly("alotMatter");
    }

    @Test
    void negativeCacheWithinRetryWindowIsFresh() {
        FreshnessPolicy.ExistingFact fact = new FreshnessPolicy.ExistingFact(
                "R1", true, NOW.minus(java.time.Duration.ofHours(23)));
        List<String> stale = policy.staleApiIds(
                Map.of("alotMatter", fact), List.of("alotMatter"), "R1", NOW, false);
        assertThat(stale).isEmpty();
    }

    @Test
    void unchangedRceptNoWithRealPayloadIsFresh() {
        FreshnessPolicy.ExistingFact fact = new FreshnessPolicy.ExistingFact(
                "R1", false, NOW.minus(java.time.Duration.ofHours(999)));
        List<String> stale = policy.staleApiIds(
                Map.of("alotMatter", fact), List.of("alotMatter"), "R1", NOW, false);
        assertThat(stale).isEmpty();
    }

    @Test
    void forceBypassesAllRules() {
        FreshnessPolicy.ExistingFact fact = new FreshnessPolicy.ExistingFact("R1", false, NOW);
        List<String> stale = policy.staleApiIds(
                Map.of("alotMatter", fact), List.of("alotMatter", "hyslrSttus"), "R1", NOW, true);
        assertThat(stale).containsExactlyInAnyOrder("alotMatter", "hyslrSttus");
    }
}
