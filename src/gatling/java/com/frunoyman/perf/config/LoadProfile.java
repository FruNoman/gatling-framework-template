package com.frunoyman.perf.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A run's shape and its pass criteria — system properties ({@code -Dprofile=load -Drate=2}) with defaults, the way Gatling's docs pass
 * load parameters; a runner (gatlingRun, CI, a scheduled job) only sets them. In the open profiles a rate is <b>sessions per second</b>,
 * not requests: one MCP session is 3 protocol requests + {@code callsPerSession} tool calls + close.
 *
 * @param toolWeights        the tools a session calls and their relative frequency ({@code -Dtools=search:8,company_details:1} —
 *                           search 8 of 9 calls); from the real traffic's mix (logs, APM), a guess only when nothing tells
 * @param users              smoke: concurrent users, each looping sessions
 * @param rate               load / soak: sessions per second; spike: the base rate; stress: the first level
 * @param steadyState        smoke / load / soak: the plateau; stress: one level; spike: the base before and after the burst
 * @param step               stress: the rate added per level
 * @param steps              stress: how many levels
 * @param spikeFactor        spike: the burst rate = rate × this
 * @param spike              spike: how long the burst lasts
 * @param feedSize           how many records the feeders load before the run
 * @param abortFailedPercent stop the load once this share of the last sessions failed — a breaking point is found, the rest only
 *                           hammers a shared environment
 */
public record LoadProfile(Profile profile, Map<Tool, Integer> toolWeights, int users, double rate, Duration rampUp, Duration steadyState,
                          double step, int steps, double spikeFactor, Duration spike, int callsPerSession, Duration pause,
                          int feedSize, double abortFailedPercent, Thresholds thresholds) {

    /** The step from one stress level to the next, and the climb into a spike. */
    public static final Duration TRANSITION = Duration.ofSeconds(10);
    /** Open profiles: how long the users started last may finish their session before maxDuration cuts them. */
    private static final Duration DRAIN = Duration.ofSeconds(30);

    /**
     * The assertions' limits: failed requests overall; the 95th percentile of a tool call, of initialize (token check + session
     * creation) and of the other protocol requests — an order of magnitude apart, so each its own.
     */
    public record Thresholds(double maxFailedPercent, int toolCallP95Ms, int initializeP95Ms, int protocolP95Ms) {
    }

    public static LoadProfile fromSystemProperties() {
        Profile profile = Profile.valueOf(property("profile", "smoke").toUpperCase());
        return new LoadProfile(
                profile,
                toolWeights(property("tools", "search")),
                Integer.parseInt(property("users", "3")),
                Double.parseDouble(property("rate", "1")),
                seconds("rampUp", 10),
                seconds("steadyState", profile.defaultSteadyState().toSeconds()),
                Double.parseDouble(property("step", "0.5")),
                Integer.parseInt(property("steps", "5")),
                Double.parseDouble(property("spikeFactor", "3")),
                seconds("spike", 30),
                Integer.parseInt(property("callsPerSession", "5")),
                Duration.ofMillis(Long.parseLong(property("pauseMs", "1000"))),
                Integer.parseInt(property("feedSize", "200")),
                Double.parseDouble(property("abortFailedPercent", "30")),
                new Thresholds(
                        Double.parseDouble(property("maxFailedPercent", "1")),
                        Integer.parseInt(property("maxToolCallP95Ms", "3000")),
                        Integer.parseInt(property("maxInitializeP95Ms", "1500")),
                        Integer.parseInt(property("maxProtocolP95Ms", "500"))));
    }

    /** The tools of the mix, in the order given. */
    public List<Tool> tools() {
        return List.copyOf(toolWeights.keySet());
    }

    /** A tool's share of the calls, in percent. */
    public double percentOf(Tool tool) {
        int total = toolWeights.values().stream().mapToInt(Integer::intValue).sum();
        return 100.0 * toolWeights.get(tool) / total;
    }

    /** How long sessions are injected. */
    public Duration injectionDuration() {
        return switch (profile) {
            case SMOKE, LOAD -> rampUp.plus(steadyState);
            case SOAK -> steadyState;
            case STRESS -> steadyState.multipliedBy(steps).plus(TRANSITION.multipliedBy(steps - 1L));
            case SPIKE -> steadyState.plus(TRANSITION).plus(spike).plus(steadyState);
        };
    }

    /** The run's limit: the injection, plus time for the last open sessions to finish. */
    public Duration maxDuration() {
        return profile.closed() ? injectionDuration() : injectionDuration().plus(DRAIN);
    }

    /** One session's requests — to turn sessions per second into requests per second. */
    public int requestsPerSession() {
        return 4 + callsPerSession;
    }

    /** {@code search:8,company_details:1} → SEARCH 8, COMPANY_DETAILS 1; a tool without a weight weighs 1. */
    private static Map<Tool, Integer> toolWeights(String spec) {
        Map<Tool, Integer> weights = new LinkedHashMap<>();
        for (String entry : spec.split(",")) {
            String[] keyAndWeight = entry.trim().split(":");
            int weight = keyAndWeight.length > 1 ? Integer.parseInt(keyAndWeight[1].trim()) : 1;
            if (weight < 1) {
                throw new IllegalArgumentException("Tool weight must be ≥ 1: " + entry);
            }
            weights.put(Tool.of(keyAndWeight[0].trim()), weight);
        }
        return weights;
    }

    private static Duration seconds(String name, long fallback) {
        return Duration.ofSeconds(Long.parseLong(property(name, String.valueOf(fallback))));
    }

    private static String property(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
