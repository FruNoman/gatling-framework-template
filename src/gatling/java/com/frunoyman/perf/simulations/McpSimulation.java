package com.frunoyman.perf.simulations;

import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;

import java.util.ArrayList;
import java.util.List;

import com.frunoyman.perf.auth.ClientCredentialsTokenProvider;
import com.frunoyman.perf.auth.TokenStore;
import com.frunoyman.perf.config.Environment;
import com.frunoyman.perf.config.LoadProfile;
import com.frunoyman.perf.config.Profile;
import com.frunoyman.perf.config.ServiceSettings;
import com.frunoyman.perf.config.Tool;
import com.frunoyman.perf.feeders.CompanyFeeder;
import com.frunoyman.perf.mock.MockMcpServer;
import com.frunoyman.perf.protocols.Protocols;
import com.frunoyman.perf.scenario.FailureGuard;
import com.frunoyman.perf.scenario.McpScenarios;
import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.Simulation;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP sessions on the service ({@code -Denv}) under one load profile ({@code -Dprofile}, {@link Injections}) — one simulation per
 * service, parameterized, not a subclass per variant. Before the load: the token (renewed beside the run), the feeder's data and the
 * plan in the log. The verdict per profile:
 * <ul>
 *   <li>smoke — no failed request at all, p95 per request;</li>
 *   <li>load, soak — failed ≤ the limit, p95 per request;</li>
 *   <li>spike — failed ≤ the limit only (latency is expected to rise in the burst; the recovery is read from the report);</li>
 *   <li>stress — none: the result is the level where it broke (the report, the plan's times, the APM).</li>
 * </ul>
 * "close session" is never asserted: maxDuration may cut users mid-session, and an assertion on a request that never ran fails.
 */
@Slf4j
public class McpSimulation extends Simulation {

    private final LoadProfile loadProfile = LoadProfile.fromSystemProperties();
    private final ServiceSettings service = Environment.service();
    private final MockMcpServer mockServer = new MockMcpServer(Environment.mockServer());
    private final TokenStore tokenStore = new TokenStore("service", new ClientCredentialsTokenProvider(service));
    private final CompanyFeeder companyFeeder = new CompanyFeeder(loadProfile.tools(), loadProfile.feedSize());
    private final FailureGuard failureGuard = new FailureGuard(loadProfile.abortFailedPercent());

    {
        setUp(Injections.of(McpScenarios.mcpSessions(service.mcpPath(), loadProfile, companyFeeder, failureGuard), loadProfile))
                .protocols(Protocols.mcpProtocol(service.baseUrl(), tokenStore))
                .maxDuration(loadProfile.maxDuration())
                .assertions(assertions(loadProfile));
    }

    @Override
    public void before() {
        mockServer.start();
        tokenStore.start();
        companyFeeder.load();
        log.info("{} on {}, tool weights {} — plan:", loadProfile.profile(), service.baseUrl(), loadProfile.toolWeights());
        Injections.plan(loadProfile).forEach(line -> log.info("  {}", line));
    }

    @Override
    public void after() {
        tokenStore.stop();
        mockServer.stop();
    }

    private static List<Assertion> assertions(LoadProfile loadProfile) {
        Profile profile = loadProfile.profile();
        LoadProfile.Thresholds thresholds = loadProfile.thresholds();
        List<Assertion> assertions = new ArrayList<>();
        if (profile == Profile.STRESS) {
            return assertions;
        }
        assertions.add(profile == Profile.SMOKE
                ? global().failedRequests().count().is(0L)
                : global().failedRequests().percent().lte(thresholds.maxFailedPercent()));
        if (profile == Profile.SPIKE) {
            return assertions;
        }
        for (Tool tool : loadProfile.tools()) {
            assertions.add(details(tool.requestName()).responseTime().percentile(95.0).lt(thresholds.toolCallP95Ms()));
        }
        assertions.add(details("initialize").responseTime().percentile(95.0).lt(thresholds.initializeP95Ms()));
        assertions.add(details("notifications/initialized").responseTime().percentile(95.0).lt(thresholds.protocolP95Ms()));
        assertions.add(details("tools/list").responseTime().percentile(95.0).lt(thresholds.protocolP95Ms()));
        return assertions;
    }
}
