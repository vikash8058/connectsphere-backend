package com.connectsphere.payment.messaging;

import com.connectsphere.payment.dto.NotificationEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificationPublisher notificationPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationPublisher, "exchange", "test-exchange");
        ReflectionTestUtils.setField(notificationPublisher, "routingKey", "test-key");
    }

    @Test
    void sendNotification_success() {
        NotificationEventMessage message = NotificationEventMessage.builder().type("TEST").build();
        
        notificationPublisher.sendNotification(message);
        
        verify(rabbitTemplate).convertAndSend("test-exchange", "test-key", message);
    }

    @Test
    void sendNotification_failure() {
        NotificationEventMessage message = NotificationEventMessage.builder().type("TEST").build();
        doThrow(new RuntimeException("Rabbit down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(NotificationEventMessage.class));
        
        // Should catch exception internally and not rethrow
        notificationPublisher.sendNotification(message);
        
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(NotificationEventMessage.class));
    }
}
