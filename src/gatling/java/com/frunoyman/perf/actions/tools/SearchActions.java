package com.frunoyman.perf.actions.tools;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.frunoyman.perf.config.Tool;
import com.frunoyman.perf.dto.jsonrpc.JsonRpcRequest;
import com.frunoyman.perf.dto.tools.SearchRequest;
import com.frunoyman.perf.feeders.CompanyFeeder;
import com.frunoyman.perf.mcp.McpChecks;
import com.frunoyman.perf.mcp.McpHeaders;
import com.frunoyman.perf.utils.Json;
import io.gatling.javaapi.core.ChainBuilder;

/**
 * The tool {@code search_companies}: the {@link SearchRequest} the feeder put into the session → {@code tools/call}.
 */
public class SearchActions {

    private final String mcpPath;

    /** {@code mcpPath} — the service's MCP endpoint ({@code /mcp}). */
    public SearchActions(String mcpPath) {
        this.mcpPath = mcpPath;
    }

    public ChainBuilder search() {
        return exec(http(Tool.SEARCH.requestName())
                .post(mcpPath)
                .headers(McpHeaders.SESSION)
                .body(StringBody(session -> {
                    SearchRequest searchRequest = session.get(CompanyFeeder.SEARCH_REQUEST);
                    return Json.toJson(JsonRpcRequest.toolCall(Tool.SEARCH.toolName(), searchRequest));
                }))
                .check(status().is(200), McpChecks.toolSucceeded()));
    }
}
