package com.frunoyman.perf.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The run's one Jackson mapper: a DTO into a request body, an answer into its record (unknown fields ignored — an answer grows, a load
 * test must not break on it).
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize " + value.getClass().getSimpleName(), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not a " + type.getSimpleName() + ": " + abbreviate(json), e);
        }
    }

    public static String abbreviate(String text) {
        return text == null || text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
