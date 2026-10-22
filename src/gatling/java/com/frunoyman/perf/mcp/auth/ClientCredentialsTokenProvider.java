package com.frunoyman.perf.mcp.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.frunoyman.perf.mcp.config.ServiceSettings;
import com.frunoyman.perf.core.utils.Json;

/**
 * OAuth 2.0 client-credentials grant (RFC 6749 §4.4, form-encoded) — the machine-to-machine token most services take. No refresh token:
 * a renewal is a new grant. The JDK HTTP client directly — three calls around a run need no HTTP library; nothing is logged, the body
 * carries the client secret. ONBOARD: an identity provider that wants JSON or an {@code audience} — change the body here; a user's
 * login (SSO, PKCE) — another {@link TokenProvider}.
 */
public class ClientCredentialsTokenProvider implements TokenProvider {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final ServiceSettings service;

    public ClientCredentialsTokenProvider(ServiceSettings service) {
        this.service = service;
    }

    @Override
    public AccessToken fetch() {
        Map<String, String> grant = new LinkedHashMap<>();
        grant.put("grant_type", "client_credentials");
        grant.put("client_id", service.clientId());
        grant.put("client_secret", service.clientSecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(service.tokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncoded(grant)))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Client-credentials grant at " + service.tokenUrl() + ": HTTP " + response.statusCode());
            }
            return Json.fromJson(response.body(), AccessToken.class);
        } catch (IOException e) {
            throw new IllegalStateException("Client-credentials grant at " + service.tokenUrl() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during the client-credentials grant", e);
        }
    }

    private static String formEncoded(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
