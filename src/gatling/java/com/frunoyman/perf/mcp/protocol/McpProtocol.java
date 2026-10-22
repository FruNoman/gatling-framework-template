package com.frunoyman.perf.mcp.protocol;

import com.frunoyman.perf.core.protocols.Protocols;
import com.frunoyman.perf.mcp.auth.TokenStore;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * MCP's Streamable HTTP for every request: the REST base (JSON, our User-Agent, the run's counting — {@link Protocols}), JSON or an
 * event stream accepted, and the bearer token read from the {@link TokenStore} on every request — a renewed token is used at once, no
 * action passes it around.
 */
public final class McpProtocol {

    private McpProtocol() {
    }

    /** {@code baseUrl} — the host; each request names its path. */
    public static HttpProtocolBuilder of(String baseUrl, TokenStore tokenStore) {
        return Protocols.restProtocol(baseUrl)
                .acceptHeader("application/json, text/event-stream")
                .header("Authorization", session -> "Bearer " + tokenStore.token());
    }
}
