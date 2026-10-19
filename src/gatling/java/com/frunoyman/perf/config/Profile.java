package com.frunoyman.perf.config;

import java.time.Duration;

/**
 * The load profiles a service's scenario runs under ({@code -Dprofile}) — what question a run answers, independent of the service:
 * <ul>
 *   <li>{@code SMOKE} — the chain works under concurrency (every deploy); closed, a few users;</li>
 *   <li>{@code LOAD} — the release's regression gate at the expected peak (the main CronJob); open, a constant rate;</li>
 *   <li>{@code STRESS} — capacity: stairs of rate until it breaks; the knee is the result, not a pass / fail;</li>
 *   <li>{@code SPIKE} — a sudden ×N burst and the recovery after it;</li>
 *   <li>{@code SOAK} — hours at a moderate rate: leaks, pools, token renewals.</li>
 * </ul>
 * Closed (users in a loop) only for smoke; the others are open — a slow system must not lower its own load.
 */
public enum Profile {

    SMOKE(true, Duration.ofSeconds(60)),
    LOAD(false, Duration.ofMinutes(5)),
    STRESS(false, Duration.ofMinutes(2)),
    SPIKE(false, Duration.ofMinutes(1)),
    SOAK(false, Duration.ofHours(1));

    private final boolean closed;
    private final Duration defaultSteadyState;

    Profile(boolean closed, Duration defaultSteadyState) {
        this.closed = closed;
        this.defaultSteadyState = defaultSteadyState;
    }

    /** Closed: a fixed number of users each looping sessions; open: new sessions arrive at a rate, each user one session. */
    public boolean closed() {
        return closed;
    }

    /** The plateau of smoke / load / soak, one level of stress, the base before and after a spike. */
    public Duration defaultSteadyState() {
        return defaultSteadyState;
    }
}
