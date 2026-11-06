package com.linkedin.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data 
@NoArgsConstructor 
@AllArgsConstructor 
public class LoginRequest {
@NotBlank (message = "Email is Required")
    @Email (message = "Invalid email format")
    private String email;

    @NotBlank (message = "Password is Required")
    private String password;
}
