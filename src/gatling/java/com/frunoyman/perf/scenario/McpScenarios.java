package com.frunoyman.perf.scenario;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.exitBlockOnFail;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.percent;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.stopLoadGeneratorIf;

import java.util.ArrayList;
import java.util.List;

import com.frunoyman.perf.actions.McpSessionActions;
import com.frunoyman.perf.actions.tools.CompanyDetailsActions;
import com.frunoyman.perf.actions.tools.SearchActions;
import com.frunoyman.perf.config.LoadProfile;
import com.frunoyman.perf.config.Tool;
import com.frunoyman.perf.feeders.CompanyFeeder;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.ScenarioBuilder;

/**
 * What a user of an MCP client does — composition of actions only, no HTTP here. One MCP session: initialize → initialized →
 * tools/list → {@code callsPerSession} calls of the profile's tools (each picked by its weight) with a think time → close. A closed
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
                                toolCall(loadProfile, mcpPath, companyFeeder).pause(loadProfile.pause())),
                        exec(session -> session.set(SESSION_OK, true))))
                .exec(mcpSessionActions.closeSession())
                .exec(session -> {
                    failureGuard.record(!session.getBoolean(SESSION_OK));
                    return session;
                })
                .exec(stopLoadGeneratorIf(session -> failureGuard.reason(), session -> failureGuard.tripped()));
    }

    /** One tool call, the tool picked by its weight; the shares add up to exactly 100 % — a gap would be a call never made. */
    private static ChainBuilder toolCall(LoadProfile loadProfile, String mcpPath, CompanyFeeder companyFeeder) {
        List<Tool> tools = loadProfile.tools();
        if (tools.size() == 1) {
            return call(tools.get(0), mcpPath, companyFeeder);
        }
        List<Choice.WithWeight> choices = new ArrayList<>();
        double assigned = 0;
        for (int index = 0; index < tools.size(); index++) {
            Tool tool = tools.get(index);
            double share = index == tools.size() - 1 ? 100.0 - assigned : loadProfile.percentOf(tool);
            assigned += share;
            choices.add(percent(share).then(call(tool, mcpPath, companyFeeder)));
        }
        return randomSwitch().on(choices);
    }

    private static ChainBuilder call(Tool tool, String mcpPath, CompanyFeeder companyFeeder) {
        return switch (tool) {
            case SEARCH -> feed(companyFeeder.searchRequests()).exec(new SearchActions(mcpPath).search());
            case COMPANY_DETAILS -> feed(companyFeeder.companyDetailsRequests()).exec(new CompanyDetailsActions(mcpPath).getCompany());
        };
    }
}
