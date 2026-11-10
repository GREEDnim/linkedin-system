package com.linkedin.userservice.dto;

/**
 * Body of POST /api/v1/auth/logout.
 *
 * <p>Optional server-side: a client that has already discarded its tokens can call logout
 * with no body and still get a success.
 */
public record LogoutRequest(String refreshToken) {
}
