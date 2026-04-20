package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

/**
 * NotificationResponseDTO - Outbound representation of a Notification
 *
 * Returned from all GET endpoints and after creation.
 * Matches the entity fields from case study section 4.6.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponseDTO {

    private Integer notificationId;
    private Integer recipientId;
    private Integer actorId;
    private NotificationType type;
    private String message;
    private Integer targetId;
    private String targetType;
    private String deepLinkUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;
}