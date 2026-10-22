package com.frunoyman.perf.mcp.protocol;

import java.util.Map;

import com.frunoyman.perf.mcp.dto.JsonRpcRequest;

/**
 * The headers of every MCP request after initialize: the session id the server gave ({@code #{mcpSessionId}}) and the protocol
 * version. Not on the protocol: initialize has no session id yet, and a protocol header with a missing attribute fails it.
 */
public final class McpHeaders {

    public static final String SESSION_ID = "mcpSessionId";
    public static final Map<String, String> SESSION = Map.of(
            "Mcp-Session-Id", "#{" + SESSION_ID + "}",
            "MCP-Protocol-Version", JsonRpcRequest.PROTOCOL_VERSION);

    private McpHeaders() {
    }
}
