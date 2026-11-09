package com.linkedin.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Email is Required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is Required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        @NotBlank(message = "First name is Required")
        String firstName,

        @NotBlank(message = "Last name is Required")
        String lastName,

        String headline,

        String location) {
}
