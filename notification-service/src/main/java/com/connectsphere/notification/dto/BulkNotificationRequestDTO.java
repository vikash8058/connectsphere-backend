package com.connectsphere.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * BulkNotificationRequestDTO - Inbound payload for admin broadcast notifications
 *
 * Used by AdminController and notification-service bulk dispatch.
 * From case study section 4.6:
 *   "Bulk notification dispatch is available for admin broadcast and system-generated alerts."
 *
 * If recipientIds is null or empty → sends to ALL users (platform-wide broadcast).
 * If recipientIds is provided      → sends to targeted users only.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkNotificationRequestDTO {

    /**
     * Target user IDs.
     * null or empty → broadcast to all users (admin resolves all user IDs before calling)
     */
    private List<Integer> recipientIds;

    @NotBlank(message = "message is required")
    private String message;

    @NotNull(message = "type is required")
    private String type;

    /** Optional deep-link URL for the broadcast (e.g. a new feature announcement page) */
    private String deepLinkUrl;
}