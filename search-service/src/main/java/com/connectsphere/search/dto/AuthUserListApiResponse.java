package com.connectsphere.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * AuthUserListApiResponse - Wrapper matching auth-service ApiResponseDTO<List<User>>
 * Used by searchUsers() Feign call.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthUserListApiResponse {
    private boolean success;
    private String message;
    private List<UserDataDTO> data;
}
