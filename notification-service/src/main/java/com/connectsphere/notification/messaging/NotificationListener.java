package com.connectsphere.notification.messaging;

import com.connectsphere.notification.dto.CreateNotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationEventMessage;
import com.connectsphere.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * NotificationListener - RabbitMQ consumer for notification events
 *
 * Listens to the notification queue and processes incoming messages
 * published by other services (like-service, comment-service, follow-service).
 *
 * Flow:
 *   other-service → rabbitTemplate.convertAndSend(exchange, routingKey, message)
 *   → connectsphere.notification.queue
 *   → handleNotificationEvent() → notificationService.createNotification()
 *
 * If processing fails, RabbitMQ will re-queue the message by default,
 * ensuring delivery reliability.
 *
 * JSON deserialization is handled automatically by Jackson2JsonMessageConverter
 * configured in RabbitMQConfig.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;

    /**
     * Consume single notification events from the notification queue.
     * NotificationEventMessage is deserialized from JSON by Jackson.
     */
    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void handleNotificationEvent(NotificationEventMessage message) {
        log.info("RabbitMQ event received: type={} recipientId={} actorId={}",
                message.getType(), message.getRecipientId(), message.getActorId());

        try {
            CreateNotificationRequestDTO request = CreateNotificationRequestDTO.builder()
                    .recipientId(message.getRecipientId())
                    .actorId(message.getActorId())
                    .type(message.getType())
                    .message(message.getMessage())
                    .targetId(message.getTargetId())
                    .targetType(message.getTargetType())
                    .deepLinkUrl(message.getDeepLinkUrl())
                    .build();

            notificationService.createNotification(request);
            log.info("Notification created from RabbitMQ event successfully");

        } catch (Exception e) {
            log.error("Failed to process RabbitMQ notification event: {}", e.getMessage(), e);
        }
    }
}