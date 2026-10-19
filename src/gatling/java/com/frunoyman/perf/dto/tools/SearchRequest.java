package com.frunoyman.perf.dto.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Arguments of {@code search_companies}: {@code name} (required), {@code limit}. Take a tool's arguments from its {@code tools/list}
 * schema — a wrong name is a tool error, not an HTTP one. Null fields are not sent.
 */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchRequest(String name, Integer limit) {
}
