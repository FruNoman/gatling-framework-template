package com.frunoyman.perf.mcp.scenarios;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.config.Weights;
import com.frunoyman.perf.mcp.actions.CompanyToolActions;
import com.frunoyman.perf.mcp.actions.McpSessionActions;
import com.frunoyman.perf.mcp.feeders.CompanyFeeder;
import io.gatling.javaapi.core.ChainBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.exitBlockOnFail;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.percent;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.repeat;

/**
 * What an MCP client does, written out — composition of actions only, no HTTP here: open a session (initialize → initialized →
 * tools/list), call tools with a think time, close it. A session whose opening fails is dropped (a real client reconnects) — otherwise
 * one failed initialize fails every later call of it on the missing session id. Smoke calls every tool once, so no tool is left out of
 * a daily run; load makes {@code callsPerSession} calls, each tool picked by its weight ({@link #WEIGHTS}, {@code -Dweights}).
 */
public class McpScenarios {

    /**
     * The tools' shares, in percent — from the real traffic's mix (logs, APM), a guess only when nothing tells. {@code getCompany}
     * stands for a tool that costs something: 0 by default, a run opts in ({@code -Dweights=search:89,getCompany:11}).
     */
    public static final Map<String, Integer> WEIGHTS = weights();

    private final McpSessionActions session;
    private final CompanyToolActions company;
    private final CompanyFeeder feeder;

    /** {@code mcpPath} — the service's MCP endpoint. */
    public McpScenarios(String mcpPath, CompanyFeeder feeder) {
        this.session = new McpSessionActions(mcpPath);
        this.company = new CompanyToolActions(mcpPath);
        this.feeder = feeder;
    }

    /** Every tool once, each on a real record. */
    public ChainBuilder smoke(LoadProfile loadProfile) {
        return mcpSession(exec(
                feed(feeder.companyNames()).exec(company.search()).pause(loadProfile.pause()),
                feed(feeder.companyIds()).exec(company.getCompany()).pause(loadProfile.pause())));
    }

    /** {@code callsPerSession} tool calls, each picked by its weight. */
    public ChainBuilder load(LoadProfile loadProfile, Weights weights) {
        return mcpSession(repeat(loadProfile.callsPerSession()).on(
                randomSwitch().on(
                        percent(weights.of("search")).then(feed(feeder.companyNames()).exec(company.search())),
                        percent(weights.of("getCompany")).then(feed(feeder.companyIds()).exec(company.getCompany())))
                .pause(loadProfile.pause())));
    }

    /** Open the MCP session, make the calls, close it; a failed opening or call ends the session (closed if it was opened). */
    private ChainBuilder mcpSession(ChainBuilder calls) {
        return exec(exitBlockOnFail().on(
                        session.initialize(),
                        session.initialized(),
                        session.listTools(),
                        calls))
                .exec(session.closeSession());
    }

    private static Map<String, Integer> weights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("search", 100);
        weights.put("getCompany", 0);
        return weights;
    }
}
