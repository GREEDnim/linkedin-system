package com.linkedin.apigateway.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * The error body shape used by every service in the platform.
 *
 * <p>Duplicated here rather than shared through a library on purpose: a gateway that
 * depends on a module from the services it fronts is no longer an independent edge, and
 * this shape is three lines.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                List.of());
    }
}
