package com.connectsphere.notification.service;

import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.NotificationType;

import java.util.List;


public interface NotificationService {

    ApiResponseDTO<NotificationResponseDTO> createNotification(
            CreateNotificationRequestDTO request);

    ApiResponseDTO<String> sendBulkNotification(BulkNotificationRequestDTO request);

    ApiResponseDTO<String> markAsRead(Integer notificationId, Integer requestingUserId);

    ApiResponseDTO<String> markAllRead(Integer recipientId);

    ApiResponseDTO<List<NotificationResponseDTO>> getByRecipient(
            Integer recipientId, Boolean isRead);

    ApiResponseDTO<Integer> getUnreadCount(Integer recipientId);

    ApiResponseDTO<String> deleteNotification(Integer notificationId,
                                              Integer requestingUserId,
                                              String requestingUserRole);

    ApiResponseDTO<String> sendEmailAlert(EmailAlertRequestDTO request);

    ApiResponseDTO<List<NotificationResponseDTO>> getAll();

    ApiResponseDTO<List<NotificationResponseDTO>> getByType(NotificationType type);
}