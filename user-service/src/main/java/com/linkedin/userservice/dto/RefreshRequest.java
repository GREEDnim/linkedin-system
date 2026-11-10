package com.linkedin.userservice.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/v1/auth/refresh. */
public record RefreshRequest(@NotBlank(message = "Refresh token is required") String refreshToken) {
}
