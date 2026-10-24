package com.frunoyman.perf.core.stats;

import com.frunoyman.perf.core.config.Environment;
import com.frunoyman.perf.core.checks.Checks;
import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.metrics.LiveMetrics;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

import static io.gatling.javaapi.core.CoreDsl.responseTimeInMillis;

/**
 * The run's numbers per request for the summary in the log (or a chat message), the verdict and the live metrics — Gatling 3.16
 * hands its statistics to nobody. Collected once in the protocol ({@link #counted}): a request is counted when sent, its response
 * time and OK / KO when answered (KO when one of its {@link Checks} failed). An attempt with no response is a KO (a timeout, a
 * refused connection) once it is older than the request timeout; a younger one was cut by the run's end and is not counted — as
 * Gatling drops it (counting it turns a green smoke red). The same events go to {@link LiveMetrics} (the dashboard).
 * <p>
 * One run per JVM (a Job, {@code gatlingRun}): {@link #start} resets it in the simulation's {@code before()}.
 */
public final class RunStats {

    private static final Map<String, Request> REQUESTS = new ConcurrentHashMap<>();
    /** The name of the request whose response is being checked on this thread. */
    private static final ThreadLocal<String> RESPONDED = new ThreadLocal<>();
    /** gatling.conf {@code http.requestTimeout}. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static volatile Map<String, String> runInfo = Map.of();
    private static volatile Instant startedAt = Instant.now();
    private static volatile Instant endedAt;
    private static volatile Verdict verdict = Verdict.NONE;
    private static volatile boolean counting = true;
    /** The OK / KO of the last {@link #RECENT_SIZE} counted answers, oldest overwritten — what the request-based failure guard reads. */
    private static final int RECENT_SIZE = 1000;
    private static final boolean[] RECENT_KO = new boolean[RECENT_SIZE];
    private static int recentNext;
    private static int recentCount;

    private RunStats() {
    }

    /**
     * A new run: {@code runTags} describe it (simulation, env, entry point, profile); {@code shape} says the load in words
     * ("3 users", "1.0 sessions/s × 5 calls") and {@code rampUp} its ramp ("60s") for the summary.
     */
    /** A new run of {@code simulation} on {@code entryPoint} ({@code premium}, {@code data-service} …) under {@code loadProfile}. */
    public static void start(String simulation, String entryPoint, LoadProfile loadProfile) {
        Map<String, String> runTags = new LinkedHashMap<>();
        runTags.put("simulation", simulation);
        runTags.put("env", Environment.name());
        runTags.put("entry_point", entryPoint);
        runTags.put("profile", loadProfile.profile().name().toLowerCase());
        start(runTags, loadProfile.rampUp().toSeconds() + "s", loadProfile.shape());
        EmptyAnswers.reset();
        LiveMetrics.start(simulation.toLowerCase() + "_" + entryPoint);
    }

    public static void start(Map<String, String> runTags, String rampUp, String shape) {
        REQUESTS.clear();
        Map<String, String> info = new LinkedHashMap<>(runTags);
        info.put("ramp_up", rampUp);
        info.put("shape", shape);
        runInfo = Map.copyOf(info);
        startedAt = Instant.now();
        endedAt = null;
        counting = true;
        verdict = Verdict.NONE;
    }

    /**
     * The run is over — the first line of the simulation's {@code after()}. Gatling stops counting when the simulation completes, but
     * its virtual users keep sending until {@code after()} returns (a slow {@code after()} — a settle wait, a fixture read — adds
     * dozens of requests); from here those count only in {@link #sinceStart} (what the service received), not in the summary or on
     * the dashboard.
     */
    public static void stop() {
        if (counting) {
            counting = false;
            endedAt = Instant.now();
            LiveMetrics.stop();
        }
    }

    public static void end(Verdict runVerdict) {
        stop();
        verdict = runVerdict;
    }

    /** {@code request}'s OK responses and attempts since the run started, including those after {@link #stop} — what reached the service. */
    public static long[] sinceStart(String requestName) {
        Request request = REQUESTS.get(requestName);
        return request == null ? new long[] {0, 0} : new long[] {request.allOk.sum(), request.allAttempts.sum()};
    }

    /**
     * Counts every request of {@code protocol}: sent (the protocol's signing step, before each request), then answered (a
     * response-time check — Gatling runs those after all the others) as OK unless one of the request's {@link Checks} failed; the same
     * responses go to {@link LiveMetrics}. Applied once in the protocol, so the actions are plain Gatling requests.
     */
    public static HttpProtocolBuilder counted(HttpProtocolBuilder protocol) {
        return protocol
                .sign((request, session) -> {
                    sent(request.getName());
                    return request;
                })
                .checkIf((response, session) -> {
                    RESPONDED.set(response.request().getName());
                    return true;
                })
                .then(responseTimeInMillis().validate("counted", (millis, session) -> {
                    answered(RESPONDED.get(), !Checks.takeFailure(), millis);
                    return millis;
                }));
    }

    /** A request sent — by the HTTP protocol's signing step ({@link #counted}), or by a non-HTTP action (gRPC) itself. */
    public static void sent(String requestName) {
        request(requestName).attempted(counting);
    }

    /** A request answered, OK or KO — after {@link #stop} only in {@link #sinceStart}, not in the summary or the live metrics. */
    public static void answered(String requestName, boolean ok, int millis) {
        request(requestName).answered(ok, millis, counting);
        if (counting) {
            LiveMetrics.response(requestName, ok, millis);
            synchronized (RECENT_KO) {
                RECENT_KO[recentNext] = !ok;
                recentNext = (recentNext + 1) % RECENT_SIZE;
                recentCount = Math.min(RECENT_SIZE, recentCount + 1);
            }
        }
    }

    /** The failed share of the last {@code window} counted answers (at most 1000), and how many there were — for the failure guard. */
    public static double[] recentFailed(int window) {
        synchronized (RECENT_KO) {
            int n = Math.min(window, recentCount);
            int failed = 0;
            for (int i = 1; i <= n; i++) {
                failed += RECENT_KO[(recentNext - i + RECENT_SIZE) % RECENT_SIZE] ? 1 : 0;
            }
            return new double[] {n == 0 ? 0 : 100.0 * failed / n, n};
        }
    }

    /** The run's tags and shape as {@link #start} got them. */
    public static Map<String, String> runInfo() {
        return runInfo;
    }

    public static Summary summary() {
        Instant end = endedAt == null ? Instant.now() : endedAt;
        Duration duration = Duration.between(startedAt, end);
        Map<String, Numbers> perRequest = new LinkedHashMap<>();
        List<Integer> allTimes = new ArrayList<>();
        long attempts = 0;
        long ok = 0;
        List<Map.Entry<String, Request>> ordered = REQUESTS.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Request> entry) -> entry.getKey().startsWith("tools/call"))
                        .thenComparing(Map.Entry::getKey))
                .toList();
        long cutBefore = System.nanoTime() - REQUEST_TIMEOUT.toNanos();
        for (Map.Entry<String, Request> entry : ordered) {
            Request request = entry.getValue();
            List<Integer> times = request.times();
            long counted = request.attempts.sum() - request.cutByEnd(cutBefore);
            perRequest.put(entry.getKey(), Numbers.of(counted, request.ok.sum(), times, duration));
            allTimes.addAll(times);
            attempts += counted;
            ok += request.ok.sum();
        }
        return new Summary(startedAt, end, Numbers.of(attempts, ok, allTimes, duration), perRequest, verdict);
    }

    private static Request request(String name) {
        return REQUESTS.computeIfAbsent(name, key -> new Request());
    }

    /** The verdict of the run's own thresholds (the simulation's assertions); NONE for a profile without one (stress). */
    public record Verdict(Boolean green, List<String> reasons) {

        public static final Verdict NONE = new Verdict(null, List.of());
    }

    public record Summary(Instant start, Instant end, Numbers global, Map<String, Numbers> perRequest, Verdict verdict) {

        public Duration duration() {
            return Duration.between(start, end);
        }
    }

    /** One request's (or all requests') numbers; times in ms over the responses that came, OK and KO. */
    public record Numbers(long count, long ok, long ko, int min, int p50, int p95, int p99, int max, double perSecond) {

        static Numbers of(long attempts, long ok, List<Integer> times, Duration duration) {
            List<Integer> sorted = times.stream().sorted().toList();
            double seconds = Math.max(duration.toMillis() / 1000.0, 1);
            return new Numbers(attempts, ok, Math.max(attempts - ok, 0), percentile(sorted, 0), percentile(sorted, 50),
                    percentile(sorted, 95), percentile(sorted, 99), percentile(sorted, 100), attempts / seconds);
        }

        public double errorPercent() {
            return count == 0 ? 0 : 100.0 * ko / count;
        }

        /** Nearest-rank percentile; 0 for no data. */
        private static int percentile(List<Integer> sorted, int percent) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
            return sorted.get(Math.min(Math.max(rank, 1), sorted.size()) - 1);
        }
    }

    private static final class Request {

        private final LongAdder attempts = new LongAdder();
        private final LongAdder ok = new LongAdder();
        private final LongAdder allAttempts = new LongAdder();
        private final LongAdder allOk = new LongAdder();
        private final List<Integer> times = new ArrayList<>();
        /** Send times (System.nanoTime) of the attempts still without a response, oldest first. */
        private final Queue<Long> pending = new ConcurrentLinkedQueue<>();


        void attempted(boolean counted) {
            allAttempts.increment();
            if (counted) {
                pending.add(System.nanoTime());
                attempts.increment();
            }
        }

        void answered(boolean succeeded, int millis, boolean counted) {
            if (succeeded) {
                allOk.increment();
            }
            if (!counted) {
                return;
            }
            pending.poll();
            synchronized (times) {
                times.add(millis);
            }
            if (succeeded) {
                ok.increment();
            }
        }

        /** Attempts without a response sent after {@code cutBefore} — the run ended under them. */
        long cutByEnd(long cutBefore) {
            return pending.stream().filter(sentAt -> sentAt > cutBefore).count();
        }

        List<Integer> times() {
            synchronized (times) {
                return new ArrayList<>(times);
            }
        }
    }
}
