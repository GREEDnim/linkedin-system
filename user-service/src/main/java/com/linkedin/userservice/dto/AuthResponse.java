package com.linkedin.userservice.dto;

import com.linkedin.userservice.entity.User;

/**
 * The token pair handed back by register, login and refresh.
 *
 * @param expiresIn access token lifetime in seconds, the conventional {@code expires_in}
 *                  field, so a client never has to decode the token to know when to refresh
 */
public record AuthResponse(
        String userId,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String email,
        String firstName,
        String lastName) {

    public static final String BEARER = "Bearer";

    public static AuthResponse of(User user, String accessToken, String refreshToken, long expiresIn) {
        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                BEARER,
                expiresIn,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName());
    }
}
