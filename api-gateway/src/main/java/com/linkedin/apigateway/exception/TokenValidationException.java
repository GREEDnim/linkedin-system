package com.linkedin.apigateway.exception;

/**
 * A token that parsed but is not acceptable: wrong {@code type} claim, or no
 * {@code userId} to forward downstream.
 *
 * <p>Kept separate from jjwt's own exceptions so the filter answers every rejection
 * with the same 401 body and a specific reason.
 */
public class TokenValidationException extends RuntimeException {

    public TokenValidationException(String message) {
        super(message);
    }
}
