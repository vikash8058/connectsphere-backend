package com.connectsphere.like.messaging;

import lombok.*;
import java.io.Serializable;

/**
 * NotificationEventMessage - RabbitMQ message payload for like-service
 *
 * Published to RabbitMQ exchange after a successful like.
 * Consumed by notification-service → NotificationListener.
 *
 * Uses String for type (not enum) to avoid cross-service enum dependency.
 * notification-service will parse the String into its own NotificationType enum.
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
    private String type;        // "LIKE", "COMMENT", "REPLY", "FOLLOW", "MENTION"
    private String message;
    private Integer targetId;
    private String targetType;  // "POST" or "COMMENT"
    private String deepLinkUrl;
}