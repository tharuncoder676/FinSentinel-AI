package com.finsentinel.common.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
    public static ApiErrorResponse of(String code, String message, String requestId) {
        return new ApiErrorResponse(code, message, requestId, Instant.now(), Map.of());
    }

    public static ApiErrorResponse validation(String message, String requestId, Map<String, String> fieldErrors) {
        return new ApiErrorResponse("VALIDATION_ERROR", message, requestId, Instant.now(), fieldErrors);
    }
}
