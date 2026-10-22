package com.frunoyman.perf.mcp.simulations;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.config.Profile;
import com.frunoyman.perf.core.config.Weights;
import com.frunoyman.perf.core.simulation.FailureGuard;
import com.frunoyman.perf.core.simulation.Injections;
import com.frunoyman.perf.core.simulation.Limits;
import com.frunoyman.perf.core.simulation.Sessions;
import com.frunoyman.perf.core.stats.EmptyAnswers;
import com.frunoyman.perf.core.stats.RunStats;
import com.frunoyman.perf.core.stats.RunSummaryLog;
import com.frunoyman.perf.mcp.auth.ClientCredentialsTokenProvider;
import com.frunoyman.perf.mcp.auth.TokenStore;
import com.frunoyman.perf.mcp.config.MockServerSettings;
import com.frunoyman.perf.mcp.config.ServiceSettings;
import com.frunoyman.perf.mcp.feeders.CompanyFeeder;
import com.frunoyman.perf.mcp.mock.MockMcpServer;
import com.frunoyman.perf.mcp.protocol.McpProtocol;
import com.frunoyman.perf.mcp.scenarios.McpScenarios;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Simulation;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

/**
 * MCP sessions on the service ({@code -Denv}) under one load profile ({@code -Dprofile}, {@link Injections}) — one simulation per
 * service, parameterized, no base class: the settings, the profile's defaults, the feeder, the limits and the {@code setUp} on one
 * screen. Smoke: 3 users each calling every tool; load: 1 session/s × 5 calls by weight. The verdict ({@link Limits}): smoke no failed
 * request, load / soak ≤ 1 % failed, spike failed % only, stress none; p95 initialize 1.5 s, initialized / tools/list 0.5 s, every
 * tool call 3 s. "close session" is never asserted: maxDuration may cut users mid-session.
 */
@Slf4j
public class McpSimulation extends Simulation {

    private final ServiceSettings service = ServiceSettings.load();
    private final LoadProfile loadProfile = LoadProfile.fromSystemProperties(new LoadProfile.Defaults(1, 5, Duration.ofSeconds(1), 3000, 4));
    private final Weights weights = Weights.of(McpScenarios.WEIGHTS);
    private final MockMcpServer mockServer = new MockMcpServer(MockServerSettings.load());
    private final TokenStore tokenStore = new TokenStore("service", new ClientCredentialsTokenProvider(service));
    private final CompanyFeeder feeder = new CompanyFeeder(loadProfile.feedSize());
    private final McpScenarios scenarios = new McpScenarios(service.mcpPath(), feeder);
    private final Limits limits = Limits.of(loadProfile, Map.of(
            "initialize", loadProfile.thresholds().initializeP95Ms(),
            "notifications/initialized", loadProfile.thresholds().protocolP95Ms(),
            "tools/list", loadProfile.thresholds().protocolP95Ms()), loadProfile.thresholds().callP95Ms());

    {
        ChainBuilder session = loadProfile.profile() == Profile.SMOKE ? scenarios.smoke(loadProfile) : scenarios.load(loadProfile, weights);
        setUp(Injections.of(Sessions.of("MCP sessions", loadProfile, session, new FailureGuard(loadProfile.abortFailedPercent())), loadProfile))
                .protocols(McpProtocol.of(service.baseUrl(), tokenStore))
                .maxDuration(loadProfile.maxDuration())
                .assertions(limits.assertions());
    }

    @Override
    public void before() {
        mockServer.start();
        RunStats.start(getClass().getSimpleName(), "mcp", loadProfile);
        tokenStore.start();
        feeder.load();
        log.info("{} on {}{} — weights {} — plan:", loadProfile.profile(), service.baseUrl(), service.mcpPath(), weights);
        Injections.plan(loadProfile).forEach(line -> log.info("  {}", line));
    }

    @Override
    public void after() {
        RunStats.stop();
        tokenStore.stop();
        mockServer.stop();
        RunStats.end(limits.verdict(RunStats.summary()));
        RunSummaryLog.log(RunStats.summary());
        EmptyAnswers.report();
    }
}
