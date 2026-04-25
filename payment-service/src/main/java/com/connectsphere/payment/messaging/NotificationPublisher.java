package com.connectsphere.payment.messaging;

import com.connectsphere.payment.dto.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${connectsphere.notification.exchange}")
    private String exchange;

    @Value("${connectsphere.notification.routing-key}")
    private String routingKey;

    public void sendNotification(NotificationEventMessage message) {
        try {
            log.info("Publishing notification event: type={} recipientId={}", message.getType(), message.getRecipientId());
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
        } catch (Exception e) {
            log.error("Failed to publish notification event: {}", e.getMessage());
        }
    }
}
