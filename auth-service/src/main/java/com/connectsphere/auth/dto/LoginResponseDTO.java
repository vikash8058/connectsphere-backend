package com.connectsphere.auth.dto;

import com.connectsphere.auth.entity.AuthProvider;
import com.connectsphere.auth.entity.Role;
import lombok.*;
import java.time.LocalDateTime;

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
    private Integer userId;
    private String username;
    private String fullName;
    private String email;
    private String bio;
    private String profilePicUrl;
    private Role role;
    private AuthProvider provider;
    @com.fasterxml.jackson.annotation.JsonProperty("isPasswordSet")
    private boolean isPasswordSet;
    @com.fasterxml.jackson.annotation.JsonProperty("isElite")
    private Boolean isElite;
    private LocalDateTime eliteUntil;
}