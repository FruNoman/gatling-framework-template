package com.frunoyman.perf.core.stats;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * The run's numbers as a table in the log — the same {@link RunStats} a chat message and the metrics carry, readable in a pod log
 * or a local run without opening a report.
 */
@Slf4j
public final class RunSummaryLog {

    private RunSummaryLog() {
    }

    public static void log(RunStats.Summary summary) {
        log.info("Run summary — {} s, verdict {}{}", summary.duration().toSeconds(), verdictText(summary.verdict()),
                summary.verdict().reasons().isEmpty() ? "" : ": " + String.join("; ", summary.verdict().reasons()));
        log.info(String.format("  %-52s %7s %6s %6s %7s %7s %7s %7s", "request", "count", "KO", "min", "p50", "p95", "p99", "max"));
        for (Map.Entry<String, RunStats.Numbers> entry : summary.perRequest().entrySet()) {
            log.info(row(entry.getKey(), entry.getValue()));
        }
        log.info(row("all requests", summary.global()));
    }

    public static String verdictText(RunStats.Verdict verdict) {
        if (verdict.green() == null) {
            return "none (stress)";
        }
        return verdict.green() ? "GREEN" : "RED";
    }

    private static String row(String name, RunStats.Numbers n) {
        return String.format("  %-52s %7d %6d %6d %7d %7d %7d %7d", name, n.count(), n.ko(), n.min(), n.p50(), n.p95(), n.p99(), n.max());
    }
}
