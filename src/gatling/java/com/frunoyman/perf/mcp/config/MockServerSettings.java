package com.frunoyman.perf.mcp.config;

import com.frunoyman.perf.core.config.Environment;
import com.typesafe.config.Config;

/**
 * The template's example service ({@code mock/}): on in the {@code local} environment, so a fresh clone runs with nothing installed.
 */
public record MockServerSettings(boolean enabled, int port, int failurePercent, int tokenLifetimeSeconds) {

    public static final MockServerSettings DISABLED = new MockServerSettings(false, 0, 0, 0);

    public static MockServerSettings load() {
        Config mock = Environment.block("mock-server");
        if (!mock.getBoolean("enabled")) {
            return DISABLED;
        }
        return new MockServerSettings(true, mock.getInt("port"), mock.getInt("failure-percent"), mock.getInt("token-lifetime-seconds"));
    }
}
