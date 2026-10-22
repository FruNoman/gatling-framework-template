package com.frunoyman.perf.grpcdemo.simulations;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.config.Profile;
import com.frunoyman.perf.core.config.Weights;
import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.core.simulation.FailureGuard;
import com.frunoyman.perf.core.simulation.Injections;
import com.frunoyman.perf.core.simulation.Limits;
import com.frunoyman.perf.core.simulation.Sessions;
import com.frunoyman.perf.core.stats.RunStats;
import com.frunoyman.perf.core.stats.RunSummaryLog;
import com.frunoyman.perf.grpcdemo.actions.CompanySearchActions;
import com.frunoyman.perf.grpcdemo.config.GrpcDemoSettings;
import com.frunoyman.perf.grpcdemo.mock.MockCompanySearchServer;
import com.frunoyman.perf.grpcdemo.scenarios.CompanySearchScenarios;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Simulation;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

/**
 * The example gRPC service under one load profile ({@code -Dprofile}) — no protocol: each action carries its call, the channel is
 * built in {@code before()} and closed in {@code after()}. Smoke: every RPC once per session; load: 2 sessions/s × 5 calls by
 * weight. The verdict ({@link Limits}): smoke no failed call, load ≤ 1 % failed, every RPC's p95 under 1 s.
 */
@Slf4j
public class GrpcSimulation extends Simulation {

    private static final int EVERY_RPC_P95_MS = 1000;

    private final GrpcDemoSettings settings = GrpcDemoSettings.load();
    private final LoadProfile loadProfile = LoadProfile.fromSystemProperties(new LoadProfile.Defaults(2, 5, Duration.ofMillis(200), 1000, 0));
    private final Weights weights = Weights.of(CompanySearchScenarios.WEIGHTS);
    private final MockCompanySearchServer mockServer = new MockCompanySearchServer(settings.mock());
    private final GrpcChannel channel = new GrpcChannel(settings.target(), settings.plaintext());
    private final CompanySearchScenarios scenarios = new CompanySearchScenarios(new CompanySearchActions(channel, settings.deadlineMs()));
    private final Limits limits = Limits.of(loadProfile, Map.of(), EVERY_RPC_P95_MS);

    {
        ChainBuilder session = loadProfile.profile() == Profile.SMOKE ? scenarios.smoke(loadProfile) : scenarios.load(loadProfile, weights);
        setUp(Injections.of(Sessions.of("gRPC company search", loadProfile, session, new FailureGuard(loadProfile.abortFailedPercent())),
                loadProfile))
                .maxDuration(loadProfile.maxDuration())
                .assertions(limits.assertions());
    }

    @Override
    public void before() {
        mockServer.start();
        RunStats.start(getClass().getSimpleName(), "grpc", loadProfile);
        channel.start();
        log.info("{} on {} — weights {} — plan:", loadProfile.profile(), settings.target(), weights);
        Injections.plan(loadProfile).forEach(line -> log.info("  {}", line));
    }

    @Override
    public void after() {
        RunStats.stop();
        channel.stop();
        mockServer.stop();
        RunStats.end(limits.verdict(RunStats.summary()));
        RunSummaryLog.log(RunStats.summary());
    }
}
