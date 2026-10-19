package com.frunoyman.perf.scenario;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.exitBlockOnFail;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.stopLoadGeneratorIf;
import static io.gatling.javaapi.core.CoreDsl.uniformRandomSwitch;

import java.util.List;

import com.frunoyman.perf.actions.McpSessionActions;
import com.frunoyman.perf.actions.tools.CompanyDetailsActions;
import com.frunoyman.perf.actions.tools.SearchActions;
import com.frunoyman.perf.config.LoadProfile;
import com.frunoyman.perf.config.Tool;
import com.frunoyman.perf.feeders.CompanyFeeder;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

/**
 * What a user of an MCP client does — composition of actions only, no HTTP here. One MCP session: initialize → initialized →
 * tools/list → {@code callsPerSession} calls of the profile's tools (each one of them at random) with a think time → close. A closed
 * profile loops sessions per user; an open one gives each arriving user one session. A session whose opening fails is dropped (a real
 * client reconnects) — otherwise one failed initialize fails every later request of it on the missing session id. Every session
 * reports to the {@link FailureGuard}, which stops the load at a breaking point.
 */
public final class McpScenarios {

    private static final String SESSION_OK = "mcpSessionOk";

    private McpScenarios() {
    }

    public static ScenarioBuilder mcpSessions(String mcpPath, LoadProfile loadProfile, CompanyFeeder companyFeeder,
                                              FailureGuard failureGuard) {
        ChainBuilder session = mcpSession(mcpPath, loadProfile, companyFeeder, failureGuard);
        return loadProfile.profile().closed() ? scenario("MCP sessions").forever().on(session) : scenario("MCP sessions").exec(session);
    }

    private static ChainBuilder mcpSession(String mcpPath, LoadProfile loadProfile, CompanyFeeder companyFeeder,
                                           FailureGuard failureGuard) {
        McpSessionActions mcpSessionActions = new McpSessionActions(mcpPath);
        return exec(session -> session.set(SESSION_OK, false))
                .exec(exitBlockOnFail().on(
                        mcpSessionActions.initialize(),
                        mcpSessionActions.initialized(),
                        mcpSessionActions.listTools(),
                        repeat(loadProfile.callsPerSession()).on(
                                toolCall(loadProfile.tools(), mcpPath, companyFeeder).pause(loadProfile.pause())),
                        exec(session -> session.set(SESSION_OK, true))))
                .exec(mcpSessionActions.closeSession())
                .exec(session -> {
                    failureGuard.record(!session.getBoolean(SESSION_OK));
                    return session;
                })
                .exec(stopLoadGeneratorIf(session -> failureGuard.reason(), session -> failureGuard.tripped()));
    }

    private static ChainBuilder toolCall(List<Tool> tools, String mcpPath, CompanyFeeder companyFeeder) {
        SearchActions searchActions = new SearchActions(mcpPath);
        CompanyDetailsActions companyDetailsActions = new CompanyDetailsActions(mcpPath);
        List<ChainBuilder> calls = tools.stream().map(tool -> switch (tool) {
            case SEARCH -> feed(companyFeeder.searchRequests()).exec(searchActions.search());
            case COMPANY_DETAILS -> feed(companyFeeder.companyDetailsRequests()).exec(companyDetailsActions.getCompany());
        }).toList();
        return calls.size() == 1 ? calls.get(0) : uniformRandomSwitch().on(calls);
    }
}
