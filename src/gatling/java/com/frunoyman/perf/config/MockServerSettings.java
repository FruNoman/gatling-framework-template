package com.frunoyman.perf.config;

/**
 * The template's example service ({@code mock/}): on in the {@code local} environment, so a fresh clone runs with nothing installed.
 */
public record MockServerSettings(boolean enabled, int port, int failurePercent, int tokenLifetimeSeconds) {

    public static final MockServerSettings DISABLED = new MockServerSettings(false, 0, 0, 0);
}
