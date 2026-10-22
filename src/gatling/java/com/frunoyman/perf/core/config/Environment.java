package com.frunoyman.perf.core.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * The environment a run targets — the {@code -Denv} block ({@code local} by default) of {@code application.conf}. Each service
 * reads its own part ({@link #block}) into its settings record; a missing value (an unset secret) fails with its path and the
 * environment variable to set. HOCON is Typesafe Config, already on Gatling's classpath; any value is overridable with {@code -D}.
 */
public final class Environment {

    private Environment() {
    }

    public static String name() {
        return System.getProperty("env", "local");
    }

    /** {@code path} of the current environment's block: {@code service}, {@code data-source} … */
    public static Config block(String path) {
        Config all = ConfigFactory.load();
        if (!all.hasPath(name())) {
            throw new IllegalStateException("No environment '" + name() + "' in application.conf");
        }
        return all.getConfig(name()).getConfig(path);
    }

    /** A value that must be set — a secret, usually. */
    public static String required(Config config, String path) {
        try {
            return config.getString(path);
        } catch (ConfigException.Missing e) {
            throw new IllegalStateException("No " + path + " in " + name() + " (" + config.origin().description()
                    + ") — the environment variable behind it is not set; locally: .env/local.env", e);
        }
    }

    /** A value that may be empty or absent. */
    public static String optional(Config config, String path) {
        return config.hasPath(path) && !config.getString(path).isBlank() ? config.getString(path) : null;
    }
}
