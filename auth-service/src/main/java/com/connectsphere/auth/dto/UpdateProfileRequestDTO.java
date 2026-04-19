package com.connectsphere.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * UpdateProfileRequestDTO - Payload for profile update
 *
 * Users can update username, fullName, bio, and profilePicUrl.
 * Email is NOT updatable (it is the login identifier).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequestDTO {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._]+$",
             message = "Username can only contain letters, numbers, dots and underscores")
    private String username;

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Size(max = 300, message = "Bio cannot exceed 300 characters")
    private String bio;

    @Size(max = 500, message = "Profile picture URL too long")
    private String profilePicUrl;
}