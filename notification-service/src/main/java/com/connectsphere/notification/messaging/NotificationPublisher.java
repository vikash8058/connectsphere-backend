package com.connectsphere.notification.messaging;

import com.connectsphere.notification.dto.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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