package com.connectsphere.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;


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