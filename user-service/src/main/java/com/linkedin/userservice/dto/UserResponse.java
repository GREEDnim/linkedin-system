package com.linkedin.userservice.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.linkedin.userservice.entity.User;
import com.linkedin.userservice.entity.UserRole;

public record UserResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String headline,
        String about,
        String location,
        String profilePhotoUrl,
        String coverPhotoUrl,
        UserRole role,
        List<String> skills,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getHeadline(),
                user.getAbout(),
                user.getLocation(),
                user.getProfilePhotoUrl(),
                user.getCoverPhotoUrl(),
                user.getRole(),
                // COPY, never hand out the Hibernate PersistentBag: it cannot be
                // read once the transaction (and its session) has closed.
                user.getSkills() == null ? List.of() : new ArrayList<>(user.getSkills()),
                user.getCreatedAt());
    }
}
