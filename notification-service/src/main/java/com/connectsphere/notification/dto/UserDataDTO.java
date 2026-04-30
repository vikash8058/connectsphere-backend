package com.connectsphere.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDataDTO {

    private boolean success;
    private UserDTO data;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserDTO {
        private Integer userId;
        private String username;
        private String fullName;
        private String email;
        private String profilePicUrl;
    }
}