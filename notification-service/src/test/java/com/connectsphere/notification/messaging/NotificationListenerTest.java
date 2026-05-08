package com.connectsphere.notification.messaging;

import com.connectsphere.notification.dto.NotificationEventMessage;
import com.connectsphere.notification.dto.NotificationResponseDTO;
import com.connectsphere.notification.dto.ApiResponseDTO;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock private NotificationService notificationService;
    @InjectMocks private NotificationListener notificationListener;

    @Test
    void handleNotificationEvent_success() {
        NotificationEventMessage msg = NotificationEventMessage.builder()
                .recipientId(1).actorId(2).type(NotificationType.LIKE)
                .message("liked").targetId(10).targetType("POST").deepLinkUrl("/posts/10").build();

        when(notificationService.createNotification(any()))
                .thenReturn(ApiResponseDTO.<NotificationResponseDTO>builder().success(true).build());

        notificationListener.handleNotificationEvent(msg);
        verify(notificationService).createNotification(any());
    }

    @Test
    void handleNotificationEvent_exception() {
        NotificationEventMessage msg = NotificationEventMessage.builder()
                .recipientId(1).actorId(2).type(NotificationType.LIKE)
                .message("liked").build();

        when(notificationService.createNotification(any()))
                .thenThrow(new RuntimeException("DB down"));

        notificationListener.handleNotificationEvent(msg);
        verify(notificationService).createNotification(any());
    }
}
