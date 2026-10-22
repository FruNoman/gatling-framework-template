package com.frunoyman.perf.core.checks;

import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.http.HttpDsl;

import java.util.Arrays;

/**
 * The checks of this project's requests — use these instead of Gatling's {@code status()} and plain validators. Gatling evaluates
 * every check of a response even after one failed and tells nobody which one failed, so a failing check here also marks the response
 * failed for the run's own counting ({@code RunStats}, the live metrics); a plain Gatling check would fail in Gatling's report and be
 * counted OK in the run's summary and on the dashboard.
 */
public final class Checks {

    private static final ThreadLocal<Boolean> FAILED = ThreadLocal.withInitial(() -> false);

    private Checks() {
    }

    /** The HTTP status is one of {@code expected}. */
    public static CheckBuilder.Final status(int... expected) {
        return HttpDsl.status().validate("status " + Arrays.toString(expected), (code, session) -> {
            if (code == null || Arrays.stream(expected).noneMatch(value -> value == code)) {
                throw failed("status " + code + ", expected " + Arrays.toString(expected));
            }
            return code;
        });
    }

    /** A project validator's failure: marks the response failed and returns the exception to throw. */
    public static IllegalStateException failed(String reason) {
        FAILED.set(true);
        return new IllegalStateException(reason);
    }

    /**
     * Whether a check of the response being processed failed, then cleared — read once per response, by the run's counting, after
     * all the request's checks (a response's checks run one after another on one thread).
     */
    public static boolean takeFailure() {
        boolean failed = FAILED.get();
        FAILED.set(false);
        return failed;
    }
}
