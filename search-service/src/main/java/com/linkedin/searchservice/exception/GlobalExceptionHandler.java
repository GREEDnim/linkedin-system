package com.linkedin.searchservice.exception;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns exceptions into a consistent JSON body plus a meaningful status code,
 * instead of letting everything surface as HTTP 500. Trimmed to what this service
 * can actually throw: no request bodies, no uploads, no relational database.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("Request failed [{}]: {}", ex.getStatus().value(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), List.of());
    }

    /** Constraint violations on @RequestParam / @PathVariable (@NotBlank, @Min, @Max). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        // Prefix each problem with its parameter name ("size: must be less than or
        // equal to 50"), mirroring the "field: message" shape used everywhere else.
        List<String> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName()
                                + ": " + error.getDefaultMessage()))
                .toList();

        if (details.isEmpty()) {
            details = ex.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .toList();
        }

        return build(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    /** Required header missing, e.g. no X-User-Id from the gateway. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Required header is missing: " + ex.getHeaderName(), List.of());
    }

    /** Required query parameter missing, e.g. a search without ?q= . */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Required parameter is missing: " + ex.getParameterName(), List.of());
    }

    /** Wrong type in a path variable or query param, e.g. ?page=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for '" + ex.getName() + "'", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint", List.of());
    }

    /** Unknown URL: 404 rather than 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Endpoint not found: /" + ex.getResourcePath(), List.of());
    }

    /**
     * Elasticsearch answered with an error (bad mapping, rejected query, ...). Spring Data
     * wraps those in UncategorizedElasticsearchException, whose cause is the client's
     * ElasticsearchException. That is our bug or a configuration problem, so 500.
     */
    @ExceptionHandler(UncategorizedElasticsearchException.class)
    public ResponseEntity<ErrorResponse> handleElasticsearchRejection(UncategorizedElasticsearchException ex) {
        log.error("Elasticsearch rejected the request", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Search backend rejected the request", List.of());
    }

    /**
     * Any other Spring Data access failure is treated as infrastructure: cluster down,
     * connection dropped, index missing. Our queries are static and validated, so an error
     * here is almost never the caller's fault.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex) {
        log.error("Search backend is unavailable", ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Search is temporarily unavailable", List.of());
    }

    /** Anything else unexpected: never leak a stack trace to the client, but always log it. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, details));
    }
}
