package com.connectsphere.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * AssignRoleRequestDTO - Payload for ADMIN to assign/change a user's role.
 *
 * Used by: PUT /auth/admin/users/{userId}/role
 *
 * role must be one of: USER, ADMIN, MODERATOR
 * Only ADMIN can call this endpoint.
 *
 * Example:
 * {
 *   "role": "MODERATOR"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignRoleRequestDTO {

    @NotBlank(message = "Role is required")
    private String role;
}