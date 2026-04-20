package com.connectsphere.notification.messaging;

import com.connectsphere.notification.dto.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * NotificationPublisher - RabbitMQ producer helper
 *
 * This bean is available within notification-service for any internal re-publishing needs.
 * Other services (like-service, comment-service, follow-service) would include their own
 * copy of this publisher or call the /notifications/internal REST endpoint directly.
 *
 * Usage example (from like-service after a post is liked):
 *
 *   notificationPublisher.publish(NotificationEventMessage.builder()
 *       .recipientId(post.getAuthorId())
 *       .actorId(likerId)
 *       .type(NotificationType.LIKE)
 *       .message("vikash liked your post")
 *       .targetId(postId)
 *       .targetType("POST")
 *       .deepLinkUrl("/posts/" + postId)
 *       .build());
 *
 * Fire-and-forget — caller does not wait for delivery confirmation.
 * Graceful degradation on RabbitMQ failure (logs error, does not fail the caller).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${notification.rabbitmq.exchange}")
    private String exchange;

    @Value("${notification.rabbitmq.routing-key}")
    private String routingKey;

    /**
     * Publish a single notification event to RabbitMQ exchange.
     * Jackson2JsonMessageConverter serializes the message as JSON.
     */
    public void publish(NotificationEventMessage message) {
        try {
            log.info("Publishing notification event: type={} recipientId={}",
                    message.getType(), message.getRecipientId());
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.debug("Notification event published to RabbitMQ successfully");
        } catch (Exception e) {
            log.error("Failed to publish notification event to RabbitMQ: {}", e.getMessage(), e);
            // Graceful degradation — don't fail the caller's primary operation
        }
    }
}