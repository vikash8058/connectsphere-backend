package com.connectsphere.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * UserSearchResultDTO - User result returned by searchUsers()
 *
 * Data fetched from auth-service via Feign.
 * Used for user search by username or fullName (case study section 2.2, 2.3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSearchResultDTO {

    private Integer userId;
    private String username;
    private String fullName;
    private String bio;
    private String profilePicUrl;
    private String role;
}
