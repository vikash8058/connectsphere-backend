package com.connectsphere.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * UserDataDTO - User data returned by auth-service Feign client
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDataDTO {

    private Integer userId;
    private String username;
    private String email;
    private String fullName;
    private String bio;
    private String profilePicUrl;
    private String role;
    private Boolean isActive;
}
