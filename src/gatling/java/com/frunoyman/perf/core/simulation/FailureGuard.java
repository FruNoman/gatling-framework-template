package com.frunoyman.perf.core.simulation;

import java.util.ArrayDeque;
import java.util.Deque;

import lombok.extern.slf4j.Slf4j;

/**
 * The share of failed sessions among the last {@link #WINDOW} — tripped above a limit, so a stress run stops at its breaking point
 * instead of hammering a shared environment for the rest of its stairs. Gatling's own stats are not visible to a virtual user;
 * the sessions report here. The trip is logged once as a warning with the time — the stop itself is no error, and Gatling reports it
 * only at INFO.
 */
@Slf4j
public class FailureGuard {

    private static final int WINDOW = 100;

    private final double limitPercent;
    private final Deque<Boolean> last = new ArrayDeque<>();
    private int failed;
    private boolean reported;

    public FailureGuard(double limitPercent) {
        this.limitPercent = limitPercent;
    }

    public synchronized void record(boolean sessionFailed) {
        last.addLast(sessionFailed);
        failed += sessionFailed ? 1 : 0;
        if (last.size() > WINDOW) {
            failed -= last.removeFirst() ? 1 : 0;
        }
    }

    public synchronized boolean tripped() {
        boolean tripped = last.size() == WINDOW && failedPercent() > limitPercent;
        if (tripped && !reported) {
            reported = true;
            log.warn("Load stopped: {}", reason());
        }
        return tripped;
    }

    public synchronized String reason() {
        return String.format("%.0f %% of the last %d sessions failed (limit %.0f %%) — breaking point reached", failedPercent(), WINDOW,
                limitPercent);
    }

    private double failedPercent() {
        return last.isEmpty() ? 0 : 100.0 * failed / last.size();
    }
}
