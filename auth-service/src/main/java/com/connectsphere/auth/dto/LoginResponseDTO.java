package com.connectsphere.auth.dto;

import com.connectsphere.auth.entity.AuthProvider;
import com.connectsphere.auth.entity.Role;
import lombok.*;

/**
 * LoginResponseDTO - Sent after successful login
 *
 * Contains JWT tokens + user profile details needed by the frontend
 * to initialize the user session and display the dashboard.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDTO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;

    // User profile data
    private Integer userId;
    private String username;
    private String fullName;
    private String email;
    private String bio;
    private String profilePicUrl;
    private Role role;
    private AuthProvider provider;
    private boolean isPasswordSet;
}