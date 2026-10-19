package com.frunoyman.perf.dto.jsonrpc;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * A JSON-RPC 2.0 message as MCP sends it — a request has an {@code id}, a notification none; null fields are not sent. The messages
 * of an MCP session come from the factories below.
 */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(String jsonrpc, Integer id, String method, Object params) {

    public static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String VERSION = "2.0";

    public static JsonRpcRequest initialize(String clientName) {
        return builder().jsonrpc(VERSION).id(1).method("initialize")
                .params(new InitializeParams(PROTOCOL_VERSION, Map.of(), new ClientInfo(clientName, "1.0")))
                .build();
    }

    public static JsonRpcRequest initialized() {
        return builder().jsonrpc(VERSION).method("notifications/initialized").build();
    }

    public static JsonRpcRequest toolsList() {
        return builder().jsonrpc(VERSION).id(2).method("tools/list").build();
    }

    /** {@code arguments} — the tool's request DTO. */
    public static JsonRpcRequest toolCall(String tool, Object arguments) {
        return builder().jsonrpc(VERSION).id(3).method("tools/call").params(new ToolCallParams(tool, arguments)).build();
    }

    public record InitializeParams(String protocolVersion, Map<String, Object> capabilities, ClientInfo clientInfo) {
    }

    public record ClientInfo(String name, String version) {
    }

    public record ToolCallParams(String name, Object arguments) {
    }
}
