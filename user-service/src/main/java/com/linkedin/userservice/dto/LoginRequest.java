package com.linkedin.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Email is Required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is Required")
        String password) {
}
