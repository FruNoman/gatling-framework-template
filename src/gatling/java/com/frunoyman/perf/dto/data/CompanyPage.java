package com.frunoyman.perf.dto.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The data source's answer: one random sample of records.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanyPage(List<Company> items) {
}
