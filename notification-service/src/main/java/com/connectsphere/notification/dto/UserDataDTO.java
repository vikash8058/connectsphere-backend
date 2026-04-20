package com.connectsphere.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * UserDataDTO - Represents user info fetched from auth-service
 *
 * Maps to the response of GET /auth/users/{userId}
 * Used to get actor username (for real message) and recipient email (for email alert)
 *
 * @JsonIgnoreProperties — ignores extra fields auth-service returns
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDataDTO {

    private boolean success;
    private UserInfo data;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfo {
        private Integer userId;
        private String username;
        private String fullName;
        private String email;
    }
}