package com.linkedin.userservice.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/v1/users/{userId}/profile.
 * All components are optional so callers can send a partial update; only non-null
 * fields are applied. Deliberately separate from UserResponse, which carries
 * server-owned fields (id, role, createdAt, email) a client must never set.
 */
public record UpdateProfileRequest(

        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 220) String headline,
        @Size(max = 2600) String about,
        @Size(max = 150) String location,
        @Size(max = 500) String profilePhotoUrl,
        @Size(max = 500) String coverPhotoUrl,
        List<@Size(max = 100) String> skills) {
}
