package com.connectsphere.auth.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * RegisterRequestDTO - Payload for LOCAL email+password registration
 *
 * username and email must be unique across the platform.
 * password follows strong password policy.
 * After registration, OTP is sent to email for verification.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDTO {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._]+$",
             message = "Username can only contain letters, numbers, dots and underscores")
    private String username;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$",
        message = "Password must contain digit, lowercase, uppercase and special character"
    )
    private String password;
}