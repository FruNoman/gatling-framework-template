package com.frunoyman.perf.mcp.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A JSON-RPC answer: a result or an error. For {@code tools/call} the result carries the tool's content and {@code isError} — a tool's
 * refusal is an HTTP 200 with {@code isError: true}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResponse(Result result, Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@JsonProperty("isError") boolean toolError, List<Content> content) {

        /** The first text block — a refusal's reason. */
        public String text() {
            return content == null || content.isEmpty() ? "" : content.get(0).text();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(String type, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(int code, String message) {
    }
}
