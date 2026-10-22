package com.frunoyman.perf.mcp.auth;

/**
 * Where the run's token comes from. Runs outside Gatling — before the load and in {@link TokenStore}'s renewal thread, never
 * in a virtual user.
 */
public interface TokenProvider {

    AccessToken fetch();

    /** A new token in place of {@code current}; a fresh {@link #fetch()} unless the provider can refresh. */
    default AccessToken renew(AccessToken current) {
        return fetch();
    }
}
