package com.frunoyman.perf.core.protocols;

import com.frunoyman.perf.core.stats.RunStats;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * The transport of a REST simulation, declared once and applied to every request: JSON in and out, a User-Agent of our own — a web
 * firewall in front of a service may refuse a client without one (403) — and the run's counting ({@code RunStats.counted}). A
 * service's own transport builds on it ({@code mcp.protocol.McpProtocol}).
 */
public final class Protocols {

    public static final String USER_AGENT = "gatling-load-tests/1.0";

    private Protocols() {
    }

    /** A REST service; add its auth header on top. */
    public static HttpProtocolBuilder restProtocol(String baseUrl) {
        return RunStats.counted(http.baseUrl(baseUrl)
                .contentTypeHeader("application/json")
                .acceptHeader("application/json")
                .userAgentHeader(USER_AGENT)
                .shareConnections());
    }
}
