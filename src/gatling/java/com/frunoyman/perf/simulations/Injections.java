package com.frunoyman.perf.simulations;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.frunoyman.perf.config.LoadProfile;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

/**
 * Each profile's injection — the load model, apart from the scenario — and its plan in words, logged at the start: the wall-clock
 * time of every level / phase, to read the report's charts and the APM / Grafana windows against.
 */
public final class Injections {

    private Injections() {
    }

    public static PopulationBuilder of(ScenarioBuilder scenario, LoadProfile p) {
        return switch (p.profile()) {
            case SMOKE -> scenario.injectClosed(
                    rampConcurrentUsers(0).to(p.users()).during(p.rampUp()),
                    constantConcurrentUsers(p.users()).during(p.steadyState()));
            case LOAD -> scenario.injectOpen(
                    rampUsersPerSec(0).to(p.rate()).during(p.rampUp()),
                    constantUsersPerSec(p.rate()).during(p.steadyState()));
            case STRESS -> scenario.injectOpen(
                    incrementUsersPerSec(p.step())
                            .times(p.steps())
                            .eachLevelLasting(p.steadyState())
                            .separatedByRampsLasting(LoadProfile.TRANSITION)
                            .startingFrom(p.rate()));
            case SPIKE -> scenario.injectOpen(
                    constantUsersPerSec(p.rate()).during(p.steadyState()),
                    rampUsersPerSec(p.rate()).to(p.rate() * p.spikeFactor()).during(LoadProfile.TRANSITION),
                    constantUsersPerSec(p.rate() * p.spikeFactor()).during(p.spike()),
                    constantUsersPerSec(p.rate()).during(p.steadyState()));
            case SOAK -> scenario.injectOpen(constantUsersPerSec(p.rate()).during(p.steadyState()));
        };
    }

    /** The plan from now on: one line per level / phase, with its wall-clock start and the request rate it means. */
    public static List<String> plan(LoadProfile p) {
        LocalTime at = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<String> plan = new ArrayList<>();
        switch (p.profile()) {
            case SMOKE -> plan.add(at + "  " + p.users() + " users looping sessions for " + p.rampUp().plus(p.steadyState()).toSeconds() + " s");
            case LOAD -> {
                plan.add(at + "  ramp 0 → " + rate(p, p.rate()) + " in " + p.rampUp().toSeconds() + " s");
                plan.add(at.plus(p.rampUp()) + "  hold " + rate(p, p.rate()) + " for " + p.steadyState().toSeconds() + " s");
            }
            case STRESS -> {
                Duration levelStart = Duration.ZERO;
                for (int level = 0; level < p.steps(); level++) {
                    plan.add(at.plus(levelStart) + "  level " + (level + 1) + ": " + rate(p, p.rate() + level * p.step())
                            + " for " + p.steadyState().toSeconds() + " s");
                    levelStart = levelStart.plus(p.steadyState()).plus(LoadProfile.TRANSITION);
                }
            }
            case SPIKE -> {
                plan.add(at + "  base " + rate(p, p.rate()) + " for " + p.steadyState().toSeconds() + " s");
                plan.add(at.plus(p.steadyState()) + "  spike to " + rate(p, p.rate() * p.spikeFactor()) + " for "
                        + p.spike().toSeconds() + " s");
                plan.add(at.plus(p.steadyState()).plus(LoadProfile.TRANSITION).plus(p.spike()) + "  recovery at "
                        + rate(p, p.rate()) + " for " + p.steadyState().toSeconds() + " s");
            }
            case SOAK -> plan.add(at + "  hold " + rate(p, p.rate()) + " for " + p.steadyState().toSeconds() + " s");
        }
        return plan;
    }

    /** In a steady open model requests per second = sessions per second × requests per session, whatever a session's length. */
    private static String rate(LoadProfile p, double sessionsPerSec) {
        return String.format("%.2f sessions/s (≈ %.1f req/s)", sessionsPerSec, sessionsPerSec * p.requestsPerSession());
    }
}
