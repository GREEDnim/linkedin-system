package com.linkedin.apigateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import lombok.extern.slf4j.Slf4j;

/**
 * Errors raised after the request has been routed, where MVC's exception handling is
 * available.
 *
 * <p>The case that matters is the backend being unreachable: without this mapping the
 * caller sees a 500, which says "the gateway is broken" when the truth is "one service is
 * down and will come back". 503 says exactly that, and matches what feed-service and
 * search-service already return when Redis or Elasticsearch is unavailable.
 */
@RestControllerAdvice
@Slf4j
public class GatewayExceptionHandler {

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> downstreamUnavailable(ResourceAccessException ex) {

        // Stack traces stay in the log; the caller gets a reason, not a topology map.
        log.warn("Downstream service unreachable: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE,
                        "A downstream service is unavailable"));
    }
}
