package com.frunoyman.perf.mcp.protocol;

import com.frunoyman.perf.core.checks.Checks;
import com.frunoyman.perf.core.stats.EmptyAnswers;
import com.frunoyman.perf.core.utils.Json;
import com.frunoyman.perf.mcp.dto.JsonRpcResponse;
import io.gatling.javaapi.core.CheckBuilder;

import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.http.HttpDsl.header;

/**
 * Checks of an MCP answer, read into {@link JsonRpcResponse} whether it came as JSON or as an event stream ({@code data: {...}}). A
 * tool's refusal is an HTTP 200 with {@code isError: true} — without {@link #toolSucceeded} a wrong argument counts as OK, every call
 * refused and the run green. A failed check names the reason in Gatling's error table ("tool error: …"): {@code validate} with an
 * exception's message, because {@code bodyString().transform(…).is(…)} reports only "failed" — Gatling hides a body check's actual value.
 */
public final class McpChecks {

    private McpChecks() {
    }

    /**
     * A result, no JSON-RPC error, no tool error. An empty answer passes but is counted ({@link EmptyAnswers}) with the session value the
     * call was made for ({@code argumentKey}: {@code companyName}, {@code companyId} …) — a growing share is a data or search regression.
     */
    public static CheckBuilder.Final toolSucceeded(String requestName, String argumentKey) {
        return bodyString().validate("tool succeeded", (body, session) -> {
            JsonRpcResponse response = parse(body);
            if (response.error() != null) {
                throw Checks.failed("json-rpc error: " + response.error().message());
            }
            if (response.result() == null) {
                throw Checks.failed("no result: " + Json.abbreviate(body));
            }
            if (response.result().toolError()) {
                throw Checks.failed("tool error: " + Json.abbreviate(response.result().text()));
            }
            if (response.result().text().isBlank()) {
                EmptyAnswers.record(requestName, session.get(argumentKey));
            }
            return body;
        });
    }

    /** initialize's {@code Mcp-Session-Id} header — the session every later request names. */
    public static CheckBuilder.Final sessionId() {
        return header("Mcp-Session-Id").validate("session id", (value, session) -> {
            if (value == null || value.isBlank()) {
                throw Checks.failed("no Mcp-Session-Id header");
            }
            return value;
        });
    }

    /** A result (initialize, tools/list). */
    public static CheckBuilder.Final hasResult() {
        return bodyString().validate("has result", (body, session) -> {
            if (parse(body).result() == null) {
                throw Checks.failed("no result: " + Json.abbreviate(body));
            }
            return body;
        });
    }

    /** The answer as JSON or as the last {@code data:} line of an event stream; anything else fails the response. */
    private static JsonRpcResponse parse(String body) {
        try {
            String trimmed = body.strip();
            if (trimmed.startsWith("{")) {
                return Json.fromJson(trimmed, JsonRpcResponse.class);
            }
            String data = trimmed.lines().filter(line -> line.startsWith("data:")).reduce((first, last) -> last)
                    .orElseThrow(() -> new IllegalArgumentException("Neither JSON nor an event stream: " + Json.abbreviate(body)));
            return Json.fromJson(data.substring("data:".length()).strip(), JsonRpcResponse.class);
        } catch (IllegalArgumentException e) {
            throw Checks.failed(e.getMessage());
        }
    }
}
