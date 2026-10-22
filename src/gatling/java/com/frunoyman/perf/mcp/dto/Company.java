package com.frunoyman.perf.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A record of the data source — what the feeders turn into tool arguments. ONBOARD: the fields the service's tools need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Company(String id, String name) {
}
