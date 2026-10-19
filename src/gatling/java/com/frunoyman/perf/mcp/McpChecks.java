package com.frunoyman.perf.mcp;

import static io.gatling.javaapi.core.CoreDsl.bodyString;

import com.frunoyman.perf.dto.jsonrpc.JsonRpcResponse;
import com.frunoyman.perf.utils.Json;
import io.gatling.javaapi.core.CheckBuilder;

/**
 * Checks of an MCP answer, read into {@link JsonRpcResponse} whether it came as JSON or as an event stream ({@code data: {...}}). A
 * tool's refusal is an HTTP 200 with {@code isError: true} — without {@link #toolSucceeded()} a wrong argument counts as OK, every call
 * refused and the run green. A failed check names the reason in Gatling's error table ("tool error: …"): {@code validate} with an
 * exception's message, because {@code bodyString().transform(…).is(…)} reports only "failed" — Gatling hides a body check's actual value.
 */
public final class McpChecks {

    private McpChecks() {
    }

    /** A result, no JSON-RPC error, no tool error. */
    public static CheckBuilder.Final toolSucceeded() {
        return bodyString().validate("tool succeeded", (body, session) -> {
            JsonRpcResponse response = parse(body);
            if (response.error() != null) {
                throw new IllegalStateException("json-rpc error: " + response.error().message());
            }
            if (response.result() == null) {
                throw new IllegalStateException("no result: " + Json.abbreviate(body));
            }
            if (response.result().toolError()) {
                throw new IllegalStateException("tool error: " + Json.abbreviate(response.result().text()));
            }
            return body;
        });
    }

    /** A result (initialize, tools/list). */
    public static CheckBuilder.Final hasResult() {
        return bodyString().validate("has result", (body, session) -> {
            if (parse(body).result() == null) {
                throw new IllegalStateException("no result: " + Json.abbreviate(body));
            }
            return body;
        });
    }

    private static JsonRpcResponse parse(String body) {
        String trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            return Json.fromJson(trimmed, JsonRpcResponse.class);
        }
        String data = trimmed.lines().filter(line -> line.startsWith("data:")).reduce((first, last) -> last)
                .orElseThrow(() -> new IllegalArgumentException("Neither JSON nor an event stream: " + Json.abbreviate(body)));
        return Json.fromJson(data.substring("data:".length()).strip(), JsonRpcResponse.class);
    }
}
