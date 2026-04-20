package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * CreateNotificationRequestDTO - Inbound payload to create a single notification
 *
 * Used by:
 *   1. Direct REST call from other microservices (like-service, comment-service, etc.)
 *   2. RabbitMQ message deserialization (NotificationEventMessage maps to this)
 *
 * actorId and recipientId come from the caller — never from JWT in this service.
 * (The notification service is called by other services, not by end-users directly.)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationRequestDTO {

    @NotNull(message = "recipientId is required")
    private Integer recipientId;

    @NotNull(message = "actorId is required")
    private Integer actorId;

    @NotNull(message = "type is required")
    private NotificationType type;

    @NotNull(message = "message is required")
    private String message;

    /** Nullable — not set for FOLLOW type */
    private Integer targetId;

    /** "POST" or "COMMENT" — nullable for FOLLOW type */
    private String targetType;

    /** Frontend deep-link URL — e.g. "/posts/42", "/profile/7" */
    private String deepLinkUrl;
}