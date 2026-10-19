package com.frunoyman.perf.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * The environment a run targets — the {@code -Denv} block ({@code local} by default) of {@code application.conf}, read into typed
 * records. HOCON is Typesafe Config, already on Gatling's classpath: a block per environment, secrets as {@code ${?ENV_NAME}}, any
 * value overridable with {@code -D}. A missing value (an unset secret) fails with its path.
 */
public final class Environment {

    private static final String ENV = System.getProperty("env", "local");

    private Environment() {
    }

    public static ServiceSettings service() {
        Config service = load().getConfig("service");
        return new ServiceSettings(service.getString("base-url"), service.getString("mcp-path"), service.getString("token-url"),
                required(service, "client-id"), required(service, "client-secret"));
    }

    public static DataSourceSettings dataSource() {
        Config dataSource = load().getConfig("data-source");
        return new DataSourceSettings(dataSource.getString("url"), optional(dataSource, "username"), optional(dataSource, "password"));
    }

    public static MockServerSettings mockServer() {
        Config mock = load().getConfig("mock-server");
        if (!mock.getBoolean("enabled")) {
            return MockServerSettings.DISABLED;
        }
        return new MockServerSettings(true, mock.getInt("port"), mock.getInt("failure-percent"), mock.getInt("token-lifetime-seconds"));
    }

    private static String required(Config config, String path) {
        try {
            return config.getString(path);
        } catch (ConfigException.Missing e) {
            throw new IllegalStateException("No " + path + " in " + ENV + " (" + config.origin().description()
                    + ") — the environment variable behind it is not set; locally: .env/local.env", e);
        }
    }

    private static String optional(Config config, String path) {
        return config.hasPath(path) ? config.getString(path) : null;
    }

    private static Config load() {
        Config all = ConfigFactory.load();
        if (!all.hasPath(ENV)) {
            throw new IllegalStateException("No environment '" + ENV + "' in application.conf");
        }
        return all.getConfig(ENV);
    }
}
