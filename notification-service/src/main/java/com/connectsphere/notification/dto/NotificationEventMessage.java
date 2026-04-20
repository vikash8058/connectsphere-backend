package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
import lombok.*;

import java.io.Serializable;

/**
 * NotificationEventMessage - RabbitMQ message payload
 *
 * This is the object published to RabbitMQ by other services (like-service,
 * comment-service, follow-service) and consumed by NotificationListener in this service.
 *
 * Must implement Serializable for RabbitMQ message conversion.
 * Jackson is used for JSON serialization (via Jackson2JsonMessageConverter).
 *
 * Flow:
 *   other-service → RabbitMQ exchange → notification queue
 *   → NotificationListener.handleNotificationEvent() → NotificationService.createNotification()
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer recipientId;
    private Integer actorId;
    private NotificationType type;
    private String message;
    private Integer targetId;
    private String targetType;
    private String deepLinkUrl;
}