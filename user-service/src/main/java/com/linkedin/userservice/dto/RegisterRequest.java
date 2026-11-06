package com.linkedin.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 
@AllArgsConstructor 
public class RegisterRequest {

    @NotBlank (message = "Email is Required")
    @Email (message = "Invalid email format")
    private String email;

    @NotBlank (message = "Password is Required")
    @Size (min = 6, message = "Password must be atleast 6 characters")
    private String password;

    @NotBlank (message = "First name is Required")
    private String firstName;

  @NotBlank (message = "Last name is Required")
    private String lastName;

    private String headline;
    
    private String location;
}
