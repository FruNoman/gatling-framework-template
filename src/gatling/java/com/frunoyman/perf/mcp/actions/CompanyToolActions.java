package com.frunoyman.perf.mcp.actions;

import com.frunoyman.perf.core.checks.Checks;
import com.frunoyman.perf.mcp.dto.CompanyDetailsRequest;
import com.frunoyman.perf.mcp.dto.JsonRpcRequest;
import com.frunoyman.perf.mcp.dto.SearchRequest;
import com.frunoyman.perf.mcp.protocol.McpChecks;
import com.frunoyman.perf.mcp.protocol.McpHeaders;
import io.gatling.javaapi.core.ChainBuilder;

import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * The service's company tools — one method per tool, written out: the request name, the endpoint, the tool's arguments as its
 * {@code tools/list} schema names them (a request DTO built from the session), the checks. Reads {@code #{companyName}} /
 * {@code #{companyId}} (the feeders of {@code CompanyFeeder}). ONBOARD: an action class per resource of the service's tools.
 */
public class CompanyToolActions {

    private static final int SEARCH_LIMIT = 10;

    private final String mcpPath;

    /** {@code mcpPath} — the service's MCP endpoint ({@code /mcp}). */
    public CompanyToolActions(String mcpPath) {
        this.mcpPath = mcpPath;
    }

    /** {@code search_companies} — cheap, read-only. */
    public ChainBuilder search() {
        return exec(
                http("tools/call search_companies")
                    .post(mcpPath)
                    .headers(McpHeaders.SESSION)
                    .body(StringBody(session -> JsonRpcRequest.toolCallBody("search_companies",
                            new SearchRequest(session.getString("companyName"), SEARCH_LIMIT))))
                    .check(
                            Checks.status(200),
                            McpChecks.toolSucceeded(
                                    "tools/call search_companies",
                                    "companyName")
                    )
        );
    }

    /** {@code get_company} — stands for a tool that costs something (credits, a quota): only in the mix when a run asks for it. */
    public ChainBuilder getCompany() {
        return exec(
                http("tools/call get_company")
                    .post(mcpPath)
                    .headers(McpHeaders.SESSION)
                    .body(StringBody(session -> JsonRpcRequest.toolCallBody("get_company",
                            new CompanyDetailsRequest(session.getString("companyId"), List.of("profile")))))
                    .check(
                            Checks.status(200),
                            McpChecks.toolSucceeded(
                                    "tools/call get_company",
                                    "companyId")
                    )
        );
    }
}
