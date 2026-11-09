package com.linkedin.postservice.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Uniform error body returned by GlobalExceptionHandler.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return of(status, message, List.of());
    }

    public static ErrorResponse of(HttpStatus status, String message, List<String> details) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details == null ? List.of() : details);
    }
}
