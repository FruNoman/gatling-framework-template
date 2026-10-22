package com.frunoyman.perf.mcp.config;

import com.frunoyman.perf.core.config.Environment;
import com.typesafe.config.Config;

/**
 * Where the feeders' real data comes from (a search index, a database's API, the service's own list endpoint) — read once, before the
 * load. Basic auth when {@code username} is set. {@link #toString()} hides the password.
 */
public record DataSourceSettings(String url, String username, String password) {

    public static DataSourceSettings load() {
        Config dataSource = Environment.block("data-source");
        return new DataSourceSettings(dataSource.getString("url"), Environment.optional(dataSource, "username"),
                Environment.optional(dataSource, "password"));
    }

    public boolean authenticated() {
        return username != null && !username.isBlank();
    }

    @Override
    public String toString() {
        return "DataSourceSettings[url=" + url + ", username=" + username + "]";
    }
}
