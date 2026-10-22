package com.frunoyman.perf.mcp.config;

import com.frunoyman.perf.core.config.Environment;
import com.typesafe.config.Config;

/**
 * The service under load: the host (the protocol's base URL), the MCP endpoint's path, and the OAuth client the run's token comes
 * from. {@link #toString()} hides the secret.
 */
public record ServiceSettings(String baseUrl, String mcpPath, String tokenUrl, String clientId, String clientSecret) {

    public static ServiceSettings load() {
        Config service = Environment.block("service");
        return new ServiceSettings(service.getString("base-url"), service.getString("mcp-path"), service.getString("token-url"),
                Environment.required(service, "client-id"), Environment.required(service, "client-secret"));
    }

    @Override
    public String toString() {
        return "ServiceSettings[baseUrl=" + baseUrl + ", mcpPath=" + mcpPath + ", tokenUrl=" + tokenUrl + ", clientId=" + clientId + "]";
    }
}
