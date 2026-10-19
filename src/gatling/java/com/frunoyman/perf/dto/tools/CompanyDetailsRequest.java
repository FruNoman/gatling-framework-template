package com.frunoyman.perf.dto.tools;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Arguments of {@code get_company}: {@code company_id} (required), {@code sections}. Null fields are not sent.
 */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompanyDetailsRequest(
        @JsonProperty("company_id") String companyId,
        List<String> sections) {
}
