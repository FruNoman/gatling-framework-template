package com.frunoyman.perf.core.metrics;

import com.frunoyman.perf.core.config.Environment;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The run's numbers live on a dashboard — what Gatling's Graphite writer sent before Gatling 3.12 removed it (real-time metrics are
 * Gatling Enterprise's now), in the same shape: every second, per request and for {@code allRequests}, the {@code ok} / {@code ko}
 * / {@code all} responses of that second ({@code count}, {@code min}, {@code max}, {@code mean}, {@code stdDev},
 * {@code percentiles50/75/95/99}), and per scenario the users ({@code active}, {@code waiting}, {@code done}), as Graphite lines
 * {@code gatling.<run>.<simulation>.<request>.<status>.<field>} — <b>per-second counts</b>, which such dashboards sum (Micrometer's
 * Graphite registry sends cumulative ones). The receiver ({@code graphite.host}, port 2003) is a Graphite listener: telegraf or
 * InfluxDB in front of the board the old Gatling writer fed — look for it in the old framework's {@code gatling.conf}. No host →
 * off; not reachable → one warning, the run goes on without live metrics.
 */
@Slf4j
public final class LiveMetrics {

    private static final DateTimeFormatter RUN_ID = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);
    private static final String ALL_REQUESTS = "allRequests";
    private static final String ALL_USERS = "allUsers";

    private static final Map<String, Second> SECONDS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> DONE = new ConcurrentHashMap<>();
    private static volatile ScheduledExecutorService sender;
    private static volatile Socket socket;
    private static volatile OutputStream out;
    private static volatile String prefix;
    private static volatile String runId;
    private static volatile String simulationName;
    private static long lastSent;

    private LiveMetrics() {
    }

    /** Starts sending for {@code simulation} (the board's {@code simulation} variable); the run id is the start time, UTC. */
    public static void start(String simulation) {
        stop();
        SECONDS.clear();
        ACTIVE.clear();
        DONE.clear();
        lastSent = 0;
        runId = RUN_ID.format(Instant.now());
        simulationName = sanitize(simulation);
        prefix = "gatling." + runId + "." + simulationName + ".";
        Config graphite = Environment.block("graphite");
        String host = Environment.optional(graphite, "host");
        int port = graphite.getInt("port");
        if (host == null) {
            log.info("Live metrics off — no graphite.host in {}", Environment.name());
            return;
        }
        try {
            Socket connected = new Socket();
            connected.connect(new InetSocketAddress(host, port), 2000);
            socket = connected;
            out = connected.getOutputStream();
        } catch (IOException e) {
            log.warn("Live metrics off — {}:{} not reachable ({})", host, port, e.getMessage());
            return;
        }
        sender = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-metrics");
            thread.setDaemon(true);
            return thread;
        });
        sender.scheduleAtFixedRate(LiveMetrics::send, 1, 1, TimeUnit.SECONDS);
        log.info("Live metrics to {}:{} — simulation {}, run {}", host, port, sanitize(simulation), runId);
    }

    /** Sends the last second and closes the connection. */
    public static void stop() {
        ScheduledExecutorService running = sender;
        sender = null;
        if (running != null) {
            running.shutdownNow();
            send();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        socket = null;
        out = null;
    }

    /** The run id the board's {@code run} variable shows — null before {@link #start}. */
    public static String runId() {
        return runId;
    }

    /** The board's {@code simulation} variable for this run. */
    public static String simulation() {
        return simulationName;
    }

    public static void response(String request, boolean ok, int millis) {
        SECONDS.computeIfAbsent(request, name -> new Second()).add(ok, millis);
    }

    public static void userStarted(String scenario) {
        ACTIVE.computeIfAbsent(scenario, name -> new AtomicInteger()).incrementAndGet();
    }

    public static void userEnded(String scenario) {
        ACTIVE.computeIfAbsent(scenario, name -> new AtomicInteger()).decrementAndGet();
        DONE.computeIfAbsent(scenario, name -> new AtomicLong()).incrementAndGet();
    }

    private static synchronized void send() {
        OutputStream stream = out;
        if (stream == null) {
            return;
        }
        // InfluxDB keeps one point per series and second: two sends in one second (the last one from stop()) would overwrite
        long now = Math.max(Instant.now().getEpochSecond(), lastSent + 1);
        lastSent = now;
        StringBuilder lines = new StringBuilder();
        List<Integer> allOk = new ArrayList<>();
        List<Integer> allKo = new ArrayList<>();
        SECONDS.forEach((request, second) -> {
            List<Integer>[] taken = second.take();
            allOk.addAll(taken[0]);
            allKo.addAll(taken[1]);
            responses(lines, sanitize(request), taken[0], taken[1], now);
        });
        responses(lines, ALL_REQUESTS, allOk, allKo, now);
        int active = 0;
        long done = 0;
        for (Map.Entry<String, AtomicInteger> scenario : ACTIVE.entrySet()) {
            long scenarioDone = DONE.getOrDefault(scenario.getKey(), new AtomicLong()).get();
            users(lines, sanitize(scenario.getKey()), scenario.getValue().get(), scenarioDone, now);
            active += scenario.getValue().get();
            done += scenarioDone;
        }
        users(lines, ALL_USERS, active, done, now);
        try {
            stream.write(lines.toString().getBytes(StandardCharsets.UTF_8));
            stream.flush();
        } catch (IOException e) {
            log.warn("Live metrics stopped — {}", e.getMessage());
            out = null;
        }
    }

    private static void responses(StringBuilder lines, String request, List<Integer> ok, List<Integer> ko, long now) {
        List<Integer> all = new ArrayList<>(ok);
        all.addAll(ko);
        stats(lines, request + ".ok.", ok, now);
        stats(lines, request + ".ko.", ko, now);
        stats(lines, request + ".all.", all, now);
    }

    private static void stats(StringBuilder lines, String path, List<Integer> times, long now) {
        line(lines, path + "count", times.size(), now);
        if (times.isEmpty()) {
            return;
        }
        List<Integer> sorted = times.stream().sorted().toList();
        double mean = sorted.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = sorted.stream().mapToDouble(time -> (time - mean) * (time - mean)).average().orElse(0);
        line(lines, path + "min", sorted.getFirst(), now);
        line(lines, path + "max", sorted.getLast(), now);
        line(lines, path + "mean", Math.round(mean), now);
        line(lines, path + "stdDev", Math.round(Math.sqrt(variance)), now);
        line(lines, path + "percentiles50", percentile(sorted, 50), now);
        line(lines, path + "percentiles75", percentile(sorted, 75), now);
        line(lines, path + "percentiles95", percentile(sorted, 95), now);
        line(lines, path + "percentiles99", percentile(sorted, 99), now);
    }

    private static void users(StringBuilder lines, String scenario, long active, long done, long now) {
        String path = "users." + scenario + ".";
        line(lines, path + "active", Math.max(active, 0), now);
        line(lines, path + "waiting", 0, now);
        line(lines, path + "done", done, now);
    }

    private static void line(StringBuilder lines, String path, long value, long now) {
        lines.append(prefix).append(path).append(' ').append(value).append(' ').append(now).append('\n');
    }

    private static int percentile(List<Integer> sorted, int percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
        return sorted.get(Math.min(Math.max(rank, 1), sorted.size()) - 1);
    }

    /** A Graphite path segment: no dots (the separator), no spaces (the line's separator). */
    private static String sanitize(String name) {
        return name.replaceAll("[.\\s]+", "_");
    }

    /** One request's responses since the last send. */
    private static final class Second {

        private List<Integer> ok = new ArrayList<>();
        private List<Integer> ko = new ArrayList<>();

        synchronized void add(boolean succeeded, int millis) {
            (succeeded ? ok : ko).add(millis);
        }

        @SuppressWarnings("unchecked")
        synchronized List<Integer>[] take() {
            List<Integer>[] taken = new List[] {ok, ko};
            ok = new ArrayList<>();
            ko = new ArrayList<>();
            return taken;
        }
    }
}
