package com.frunoyman.perf.core.stats;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/**
 * Calls that answered "nothing" without an error — an empty tool text with {@code isError: false}, a single record's 404 — a
 * service's way of saying "nothing found". Not a failed request: the call worked, and a daily run must not turn red on it. Not
 * ignored either: arguments sampled from the data the service reads should find something, so the count and a few of the arguments
 * are logged after the run ({@link #report()}) — a share that grows is a data or search regression.
 */
@Slf4j
public final class EmptyAnswers {

    private static final int EXAMPLES = 5;
    private static final Map<String, LongAdder> COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> SAMPLES = new ConcurrentHashMap<>();

    private EmptyAnswers() {
    }

    public static void record(String requestName, Object arguments) {
        COUNTS.computeIfAbsent(requestName, name -> new LongAdder()).increment();
        List<String> examples = SAMPLES.computeIfAbsent(requestName, name -> new CopyOnWriteArrayList<>());
        if (examples.size() < EXAMPLES) {
            examples.add(String.valueOf(arguments));
        }
    }

    /** A new run. */
    public static void reset() {
        COUNTS.clear();
        SAMPLES.clear();
    }

    /** Empty answers per request so far. */
    public static Map<String, Long> counts() {
        Map<String, Long> counts = new TreeMap<>();
        COUNTS.forEach((requestName, count) -> counts.put(requestName, count.sum()));
        return counts;
    }

    public static void report() {
        if (COUNTS.isEmpty()) {
            log.info("Empty answers: none");
            return;
        }
        COUNTS.forEach((requestName, count) -> log.warn("Empty answers of {}: {} — e.g. {}", requestName, count.sum(),
                SAMPLES.getOrDefault(requestName, List.of())));
    }
}
