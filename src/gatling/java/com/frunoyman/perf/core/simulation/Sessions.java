package com.frunoyman.perf.core.simulation;

import com.frunoyman.perf.core.metrics.LiveMetrics;
import com.frunoyman.perf.core.config.LoadProfile;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.stopLoadGeneratorIf;

/**
 * A scenario's session as Gatling runs it under a profile: a closed profile (smoke) loops it per user, an open one gives each arriving
 * user one; every session reports to the {@link FailureGuard} (a session with any failed request counts as failed) and the load stops
 * once the guard trips. The session itself — which actions in which order — is the service's scenario class.
 */
public final class Sessions {

    private Sessions() {
    }

    public static ScenarioBuilder of(String name, LoadProfile loadProfile, ChainBuilder session, FailureGuard failureGuard) {
        ChainBuilder guarded = exec(Session::markAsSucceeded)
                .exec(session)
                .exec(s -> {
                    failureGuard.record(s.isFailed());
                    return s.markAsSucceeded();
                })
                .exec(stopLoadGeneratorIf(s -> failureGuard.reason(), s -> failureGuard.tripped()));
        ChainBuilder started = exec(s -> {
            LiveMetrics.userStarted(name);
            return s;
        });
        if (loadProfile.profile().closed()) {
            return scenario(name).exec(started).forever().on(guarded);
        }
        return scenario(name).exec(started).exec(guarded).exec(s -> {
            LiveMetrics.userEnded(name);
            return s;
        });
    }
}
