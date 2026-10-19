package com.frunoyman.perf.actions;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.doIf;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.frunoyman.perf.dto.jsonrpc.JsonRpcRequest;
import com.frunoyman.perf.mcp.McpChecks;
import com.frunoyman.perf.mcp.McpHeaders;
import com.frunoyman.perf.utils.Json;
import io.gatling.javaapi.core.ChainBuilder;

/**
 * The protocol requests of an MCP session on one endpoint, what every client does around its tool calls: initialize (the server
 * answers its session id in {@code Mcp-Session-Id}, kept as {@code #{mcpSessionId}}), the initialized notification, tools/list, close.
 */
public class McpSessionActions {

    private static final String CLIENT_NAME = "gatling-load-tests";
    private static final String INITIALIZE = Json.toJson(JsonRpcRequest.initialize(CLIENT_NAME));
    private static final String INITIALIZED = Json.toJson(JsonRpcRequest.initialized());
    private static final String TOOLS_LIST = Json.toJson(JsonRpcRequest.toolsList());

    private final String mcpPath;

    /** {@code mcpPath} — the service's MCP endpoint ({@code /mcp}). */
    public McpSessionActions(String mcpPath) {
        this.mcpPath = mcpPath;
    }

    /** Forgets the previous session's id first — a failed initialize must not reuse a closed session. */
    public ChainBuilder initialize() {
        return exec(session -> session.remove(McpHeaders.SESSION_ID))
                .exec(http("initialize")
                        .post(mcpPath)
                        .body(StringBody(INITIALIZE))
                        .check(status().is(200), header("Mcp-Session-Id").saveAs(McpHeaders.SESSION_ID), McpChecks.hasResult()));
    }

    public ChainBuilder initialized() {
        return exec(http("notifications/initialized")
                .post(mcpPath)
                .headers(McpHeaders.SESSION)
                .body(StringBody(INITIALIZED))
                .check(status().in(200, 202)));
    }

    public ChainBuilder listTools() {
        return exec(http("tools/list")
                .post(mcpPath)
                .headers(McpHeaders.SESSION)
                .body(StringBody(TOOLS_LIST))
                .check(status().is(200), McpChecks.hasResult()));
    }

    /** Closes the session if one was opened, and forgets its id. */
    public ChainBuilder closeSession() {
        return doIf(session -> session.contains(McpHeaders.SESSION_ID)).then(exec(http("close session")
                .delete(mcpPath)
                .headers(McpHeaders.SESSION)
                .check(status().in(200, 202, 204, 404, 405)))
                .exec(session -> session.remove(McpHeaders.SESSION_ID)));
    }
}
