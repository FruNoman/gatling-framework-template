package com.frunoyman.perf.core.config;

import java.time.Duration;

/**
 * A run's shape and its pass criteria — system properties ({@code -Dprofile=load -Drate=1}) with each simulation's defaults (the
 * approved load model's numbers, {@link Defaults}); a runner ({@code gatlingRun}, a Job) only sets them. In the open profiles a rate
 * is <b>sessions (visits) per second</b>, not requests: a session is {@code protocolRequestsPerSession} protocol requests (MCP:
 * initialize, initialized, tools/list, close; REST: none) + {@code callsPerSession} calls. What a session calls is the scenario's.
 *
 * @param users              smoke: concurrent users, each looping sessions
 * @param rate               load / soak: sessions per second; spike: the base rate; stress: the first level
 * @param steadyState        smoke / load / soak: the plateau; stress: one level; spike: the base before and after the burst
 * @param step               stress: the rate added per level
 * @param steps              stress: how many levels
 * @param spikeFactor        spike: the burst rate = rate × this
 * @param spike              spike: how long the burst lasts
 * @param callsPerSession    calls a session makes (load profiles; smoke walks its own fixed list)
 * @param feedSize           how many records each feeder samples before the run
 * @param abortFailedPercent stop the load once this share of the last sessions failed — a breaking point is found, the rest only
 *                           hammers a shared environment
 */
public record LoadProfile(Profile profile, int users, double rate, Duration rampUp, Duration steadyState, double step, int steps,
                          double spikeFactor, Duration spike, int callsPerSession, Duration pause, int feedSize,
                          double abortFailedPercent, int protocolRequestsPerSession, Thresholds thresholds) {

    /** The step from one stress level to the next, and the climb into a spike. */
    public static final Duration TRANSITION = Duration.ofSeconds(10);
    /** Open profiles: how long the users started last may finish their session before maxDuration cuts them. */
    private static final Duration DRAIN = Duration.ofSeconds(30);

    /** The failed share a load / soak / spike run may have; the p95 of a call and of the MCP protocol requests. */
    public record Thresholds(double maxFailedPercent, int callP95Ms, int initializeP95Ms, int protocolP95Ms) {
    }

    /** A simulation's defaults — the load model's numbers for it. */
    public record Defaults(double rate, int callsPerSession, Duration pause, int callP95Ms, int protocolRequestsPerSession) {
    }

    public static LoadProfile fromSystemProperties(Defaults defaults) {
        Profile profile = Profile.valueOf(property("profile", "smoke").toUpperCase());
        return new LoadProfile(
                profile,
                Integer.parseInt(property("users", "3")),
                Double.parseDouble(property("rate", String.valueOf(defaults.rate()))),
                seconds("rampUp", 60),
                seconds("steadyState", profile.defaultSteadyState().toSeconds()),
                Double.parseDouble(property("step", "0.5")),
                Integer.parseInt(property("steps", "6")),
                Double.parseDouble(property("spikeFactor", "4")),
                seconds("spike", 30),
                Integer.parseInt(property("callsPerSession", String.valueOf(defaults.callsPerSession()))),
                Duration.ofMillis(Long.parseLong(property("pauseMs", String.valueOf(defaults.pause().toMillis())))),
                Integer.parseInt(property("feedSize", "200")),
                Double.parseDouble(property("abortFailedPercent", "30")),
                defaults.protocolRequestsPerSession(),
                new Thresholds(
                        Double.parseDouble(property("maxFailedPercent", "1")),
                        Integer.parseInt(property("maxCallP95Ms", String.valueOf(defaults.callP95Ms()))),
                        Integer.parseInt(property("maxInitializeP95Ms", "1500")),
                        Integer.parseInt(property("maxProtocolP95Ms", "500"))));
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

    /** One session's requests in the open profiles — to turn sessions per second into requests per second. */
    public int requestsPerSession() {
        return protocolRequestsPerSession + callsPerSession;
    }

    /** "3 users" / "2.0 sessions/s × 5 calls" — the summary's and the log's words for the load. */
    public String shape() {
        return profile.closed() ? users + " users" : rate + " sessions/s × " + callsPerSession + " calls";
    }

    static String property(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Duration seconds(String name, long fallback) {
        return Duration.ofSeconds(Long.parseLong(property(name, String.valueOf(fallback))));
    }
}
