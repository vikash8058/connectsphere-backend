package com.connectsphere.auth.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * RegisterRequestDTO - Payload for LOCAL email+password registration
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDTO {

    @NotBlank(message = "Please choose a unique username to get started")
    @Size(min = 3, max = 50, message = "Your username should be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._]+$",
             message = "Usernames can only contain letters, numbers, dots, and underscores")
    private String username;

    @NotBlank(message = "Please tell us your full name")
    @Size(min = 2, max = 100, message = "Full names should be between 2 and 100 characters")
    private String fullName;

    @NotBlank(message = "An email address is required for account security")
    @Email(message = "Please enter a valid email address (e.g., name@example.com)")
    private String email;

    @NotBlank(message = "A secure password is required to protect your account")
    @Size(min = 8, message = "For your security, please use a password with at least 8 characters")
    @Pattern(
        regexp = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$",
        message = "Your password must include at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String password;

    @NotBlank(message = "Please confirm your password to ensure they match")
    private String confirmPassword;
}