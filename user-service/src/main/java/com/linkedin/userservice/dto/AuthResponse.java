package com.linkedin.userservice.dto;

import com.linkedin.userservice.entity.User;

public record AuthResponse(
        String userId,
        String accessToken,
        String refreshToken,
        String tokenType,
        String email,
        String firstName,
        String lastName) {

    public static final String BEARER = "Bearer";

    public static AuthResponse of(User user, String accessToken, String refreshToken) {
        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                BEARER,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName());
    }
}
