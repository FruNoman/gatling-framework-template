package com.frunoyman.perf.mcp.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Arguments of {@code get_company}: {@code company_id} (required), {@code sections}. Null fields are not sent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompanyDetailsRequest(
        @JsonProperty("company_id") String companyId,
        List<String> sections) {
}
