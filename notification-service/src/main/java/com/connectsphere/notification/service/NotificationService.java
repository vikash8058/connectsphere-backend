package com.connectsphere.notification.service;

import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.NotificationType;

import java.util.List;

/**
 * NotificationService - Business contract for Notification Service
 *
 * Methods as per ConnectSphere case study section 4.6:
 *   createNotification()    - Single notification (direct call or from RabbitMQ consumer)
 *   sendBulkNotification()  - Admin broadcast to list of recipients
 *   markAsRead()            - Mark one notification as read
 *   markAllRead()           - Mark all notifications of a recipient as read
 *   getByRecipient()        - Get all notifications for a user (with optional isRead filter)
 *   getUnreadCount()        - Badge count for nav bar
 *   deleteNotification()    - User/admin deletes a notification
 *   sendEmailAlert()        - Email for high-priority events
 *   getAll()                - Admin: get all notifications (platform-wide)
 *   getByType()             - Admin: filter notifications by type
 */
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