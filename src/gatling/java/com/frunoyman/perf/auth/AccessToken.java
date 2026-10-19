package com.frunoyman.perf.auth;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A token response: the access token, its life in seconds ({@code expires_in}; null when the response has none) and the refresh token
 * (null when none — the M2M grant gives none). Its values are never printed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccessToken(
        @JsonProperty("access_token") String value,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("refresh_token") String refreshToken) {

    public Duration lifetime() {
        return expiresIn == null ? null : Duration.ofSeconds(expiresIn);
    }

    public AccessToken withRefreshToken(String previous) {
        return refreshToken != null ? this : new AccessToken(value, expiresIn, previous);
    }

    @Override
    public String toString() {
        return "AccessToken[lifetime=" + lifetime() + ", refreshable=" + (refreshToken != null) + "]";
    }
}
