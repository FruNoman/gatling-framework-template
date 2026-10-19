package com.frunoyman.perf.config;

/**
 * The service under load: the host (the protocol's base URL), the MCP endpoint's path, and the OAuth client the run's token comes
 * from. {@link #toString()} hides the secret.
 */
public record ServiceSettings(String baseUrl, String mcpPath, String tokenUrl, String clientId, String clientSecret) {

    @Override
    public String toString() {
        return "ServiceSettings[baseUrl=" + baseUrl + ", mcpPath=" + mcpPath + ", tokenUrl=" + tokenUrl + ", clientId=" + clientId + "]";
    }
}
