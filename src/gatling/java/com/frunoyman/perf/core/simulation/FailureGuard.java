package com.frunoyman.perf.core.simulation;

import com.frunoyman.perf.core.stats.RunStats;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The share of failed sessions among the last {@link #WINDOW} — or, with {@code -DabortOn=requests}, of failed requests among the
 * last {@link #REQUEST_WINDOW} — tripped above a limit, so a stress run stops at its breaking point instead of hammering a shared
 * environment for the rest of its stairs. Sessions (the default) are strict: a session with any failed request counts as failed, so
 * with 7 requests a session 1 % of failed requests already spoils ~7 % of the sessions, and a few rare refusals stop a long run;
 * requests follow the failed share the verdict reads. Gatling's own stats are not visible to a virtual user;
 * the sessions report here. The trip is logged once as a warning with the time — the stop itself is no error, and Gatling reports it
 * only at INFO.
 */
@Slf4j
public class FailureGuard {

    private static final int WINDOW = 100;
    private static final int REQUEST_WINDOW = 500;

    /** What the limit is a share of. */
    public enum Basis { SESSIONS, REQUESTS }

    private final double limitPercent;
    private final Basis basis;
    private final Deque<Boolean> last = new ArrayDeque<>();
    private int failed;
    private boolean reported;

    public FailureGuard(double limitPercent) {
        this(limitPercent, Basis.valueOf(System.getProperty("abortOn", "sessions").trim().toUpperCase()));
    }

    public FailureGuard(double limitPercent, Basis basis) {
        this.limitPercent = limitPercent;
        this.basis = basis;
    }

    public synchronized void record(boolean sessionFailed) {
        last.addLast(sessionFailed);
        failed += sessionFailed ? 1 : 0;
        if (last.size() > WINDOW) {
            failed -= last.removeFirst() ? 1 : 0;
        }
    }

    public synchronized boolean tripped() {
        boolean tripped = basis == Basis.REQUESTS
                ? RunStats.recentFailed(REQUEST_WINDOW)[1] >= REQUEST_WINDOW && RunStats.recentFailed(REQUEST_WINDOW)[0] > limitPercent
                : last.size() == WINDOW && failedPercent() > limitPercent;
        if (tripped && !reported) {
            reported = true;
            log.warn("Load stopped: {}", reason());
        }
        return tripped;
    }

    public synchronized String reason() {
        if (basis == Basis.REQUESTS) {
            return String.format("%.0f %% of the last %d requests failed (limit %.0f %%) — breaking point reached",
                    RunStats.recentFailed(REQUEST_WINDOW)[0], REQUEST_WINDOW, limitPercent);
        }
        return String.format("%.0f %% of the last %d sessions failed (limit %.0f %%) — breaking point reached", failedPercent(), WINDOW,
                limitPercent);
    }

    private double failedPercent() {
        return last.isEmpty() ? 0 : 100.0 * failed / last.size();
    }
}
