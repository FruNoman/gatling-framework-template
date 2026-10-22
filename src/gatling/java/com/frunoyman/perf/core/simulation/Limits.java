package com.frunoyman.perf.core.simulation;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.config.Profile;
import com.frunoyman.perf.core.stats.RunStats;
import io.gatling.javaapi.core.Assertion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.forAll;
import static io.gatling.javaapi.core.CoreDsl.global;

/**
 * A run's limits as data — one source for Gatling's assertions and for {@link RunStats}' verdict in the run's summary: the failed
 * share (smoke: none at all; stress: no limit) and the p95 per request (not for spike or stress). "close session" is never limited:
 * maxDuration may cut it, and an assertion on a request that never ran fails.
 */
public record Limits(Long maxFailedCount, Double maxFailedPercent, Map<String, Integer> p95Ms, Integer everyRequestP95Ms) {

    /**
     * {@code p95Ms}: the requests a simulation asserts and each one's limit — written out in the simulation, the requests its scenario
     * makes (in smoke all of them; in the open profiles those a run calls often enough to have a p95).
     */
    public static Limits of(LoadProfile loadProfile, Map<String, Integer> p95Ms) {
        return of(loadProfile, p95Ms, null);
    }

    /** {@code everyRequestP95Ms}: one p95 limit for every request the run makes (a service with dozens of endpoints). */
    public static Limits of(LoadProfile loadProfile, Map<String, Integer> p95Ms, Integer everyRequestP95Ms) {
        Profile profile = loadProfile.profile();
        if (profile == Profile.STRESS) {
            return new Limits(null, null, Map.of(), null);
        }
        Long maxFailedCount = profile == Profile.SMOKE ? 0L : null;
        Double maxFailedPercent = profile == Profile.SMOKE ? null : loadProfile.thresholds().maxFailedPercent();
        boolean latency = profile != Profile.SPIKE;
        return new Limits(maxFailedCount, maxFailedPercent, latency ? new LinkedHashMap<>(p95Ms) : Map.of(), latency ? everyRequestP95Ms : null);
    }

    public List<Assertion> assertions() {
        List<Assertion> assertions = new ArrayList<>();
        if (maxFailedCount != null) {
            assertions.add(global().failedRequests().count().is(maxFailedCount));
        }
        if (maxFailedPercent != null) {
            assertions.add(global().failedRequests().percent().lte(maxFailedPercent));
        }
        p95Ms.forEach((request, limit) -> assertions.add(details(request).responseTime().percentile(95.0).lt(limit)));
        if (everyRequestP95Ms != null) {
            assertions.add(forAll().responseTime().percentile(95.0).lt(everyRequestP95Ms));
        }
        return assertions;
    }

    /** The same limits over {@link RunStats}' numbers; a request that never ran fails, as in Gatling. */
    public RunStats.Verdict verdict(RunStats.Summary summary) {
        if (maxFailedCount == null && maxFailedPercent == null && p95Ms.isEmpty() && everyRequestP95Ms == null) {
            return RunStats.Verdict.NONE;
        }
        List<String> reasons = new ArrayList<>();
        if (maxFailedCount != null && summary.global().ko() > maxFailedCount) {
            reasons.add(summary.global().ko() + " failed requests (none allowed)");
        }
        if (maxFailedPercent != null && summary.global().errorPercent() > maxFailedPercent) {
            reasons.add(String.format("%.2f %% failed (limit %.2f %%)", summary.global().errorPercent(), maxFailedPercent));
        }
        p95Ms.forEach((request, limit) -> {
            RunStats.Numbers numbers = summary.perRequest().get(request);
            if (numbers == null || numbers.count() == 0) {
                reasons.add(request + " never ran");
            } else if (numbers.p95() >= limit) {
                reasons.add(request + " p95 " + numbers.p95() + " ms (limit " + limit + ")");
            }
        });
        if (everyRequestP95Ms != null) {
            summary.perRequest().forEach((request, numbers) -> {
                if (!p95Ms.containsKey(request) && numbers.count() > 0 && numbers.p95() >= everyRequestP95Ms) {
                    reasons.add(request + " p95 " + numbers.p95() + " ms (limit " + everyRequestP95Ms + ")");
                }
            });
        }
        return new RunStats.Verdict(reasons.isEmpty(), reasons);
    }
}
