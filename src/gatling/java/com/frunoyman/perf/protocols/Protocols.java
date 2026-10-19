package com.frunoyman.perf.protocols;

import static io.gatling.javaapi.http.HttpDsl.http;

import com.frunoyman.perf.auth.TokenStore;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * The transport of a simulation, declared once and applied to every request. MCP's Streamable HTTP: JSON in, JSON or an event stream
 * out, a User-Agent of our own — a web firewall in front of a service may refuse a client without one (403) — and the bearer token, read
 * from the {@link TokenStore} on every request: a renewed token is used at once, no action passes it around.
 */
public final class Protocols {

    private static final String USER_AGENT = "gatling-load-tests/1.0";

    private Protocols() {
    }

    /** {@code baseUrl} — the host; each request names its path. */
    public static HttpProtocolBuilder mcpProtocol(String baseUrl, TokenStore tokenStore) {
        return http.baseUrl(baseUrl)
                .contentTypeHeader("application/json")
                .acceptHeader("application/json, text/event-stream")
                .userAgentHeader(USER_AGENT)
                .header("Authorization", session -> "Bearer " + tokenStore.token())
                .shareConnections();
    }
}
