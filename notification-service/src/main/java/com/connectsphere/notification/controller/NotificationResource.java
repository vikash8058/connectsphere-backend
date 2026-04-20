package com.connectsphere.notification.controller;

import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NotificationResource - REST Controller for ConnectSphere Notification Service
 *
 * Base path: /api/v1/notifications (via context-path in application.yml)
 *
 * PROTECTED (JWT required — user's own data):
 *   GET    /notifications/recipient/{recipientId}               - Get all notifications (optional ?isRead=)
 *   GET    /notifications/recipient/{recipientId}/unread-count  - Badge count for nav bar
 *   POST   /notifications/{id}/read                            - Mark one as read
 *   POST   /notifications/recipient/{recipientId}/read-all     - Mark all as read
 *   DELETE /notifications/{id}                                 - Delete one notification
 *
 * INTERNAL (service-to-service — no user JWT needed, open in SecurityConfig):
 *   POST   /notifications/internal                             - Create single notification
 *
 * ADMIN ONLY (ROLE_ADMIN via @PreAuthorize):
 *   POST   /notifications/bulk                                 - Broadcast to multiple users
 *   POST   /notifications/email-alert                         - Send email alert
 *   GET    /notifications/all                                 - Get all notifications (platform)
 *   GET    /notifications/type/{type}                         - Get by type
 *
 * userId is read from request attributes set by JwtAuthenticationFilter.
 */
@RestController
@RequestMapping("/notifications")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Notification Service",
     description = "In-app and email notifications, read-state management, RabbitMQ async dispatch")
public class NotificationResource {

    private final NotificationService notificationService;

    // ── GET NOTIFICATIONS FOR A RECIPIENT ──

    @GetMapping("/recipient/{recipientId}")
    @Operation(summary = "Get notifications for a user",
               description = "Returns all notifications for a recipient. " +
                             "Optional ?isRead=true/false to filter by read/unread state.")
    public ResponseEntity<ApiResponseDTO<List<NotificationResponseDTO>>> getByRecipient(
            @PathVariable Integer recipientId,
            @RequestParam(required = false) Boolean isRead) {
        return ResponseEntity.ok(notificationService.getByRecipient(recipientId, isRead));
    }

    // ── UNREAD COUNT (nav bar badge) ──

    @GetMapping("/recipient/{recipientId}/unread-count")
    @Operation(summary = "Get unread notification count",
               description = "Returns the unread count number for the red badge in top navigation bar")
    public ResponseEntity<ApiResponseDTO<Integer>> getUnreadCount(
            @PathVariable Integer recipientId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(recipientId));
    }

    // ── MARK ONE AS READ ──

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark a single notification as read",
               description = "Only the recipient can mark their own notification as read")
    public ResponseEntity<ApiResponseDTO<String>> markAsRead(
            @PathVariable Integer notificationId,
            HttpServletRequest httpRequest) {
        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(notificationService.markAsRead(notificationId, userId));
    }

    // ── MARK ALL AS READ ──

    @PostMapping("/recipient/{recipientId}/read-all")
    @Operation(summary = "Mark all notifications as read",
               description = "Marks all unread notifications for the recipient as read in a single bulk UPDATE")
    public ResponseEntity<ApiResponseDTO<String>> markAllRead(
            @PathVariable Integer recipientId) {
        return ResponseEntity.ok(notificationService.markAllRead(recipientId));
    }

    // ── DELETE NOTIFICATION ──

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete a notification",
               description = "Recipient can delete their own notification. Admin can delete any.")
    public ResponseEntity<ApiResponseDTO<String>> deleteNotification(
            @PathVariable Integer notificationId,
            HttpServletRequest httpRequest) {
        Integer userId = getRequestingUserId(httpRequest);
        String role = getRequestingUserRole(httpRequest);
        return ResponseEntity.ok(
                notificationService.deleteNotification(notificationId, userId, role));
    }

    // ── INTERNAL: CREATE SINGLE (service-to-service REST fallback) ──

    @PostMapping("/internal")
    @Operation(summary = "Create a notification (internal service-to-service call)",
               description = "Called by like-service, comment-service, follow-service as a " +
                             "synchronous REST fallback when RabbitMQ is unavailable. " +
                             "Primary path is async via RabbitMQ.")
    public ResponseEntity<ApiResponseDTO<NotificationResponseDTO>> createNotification(
            @Valid @RequestBody CreateNotificationRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createNotification(request));
    }

    // ── ADMIN: BULK BROADCAST ──

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send bulk notification (Admin only)",
               description = "Broadcasts a notification to a list of user IDs. " +
                             "recipientIds must be provided — Admin resolves all target user IDs.")
    public ResponseEntity<ApiResponseDTO<String>> sendBulkNotification(
            @Valid @RequestBody BulkNotificationRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.sendBulkNotification(request));
    }

    // ── ADMIN: SEND EMAIL ALERT ──

    @PostMapping("/email-alert")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send email alert (Admin only)",
               description = "Sends an async email alert for high-priority events " +
                             "(e.g. account actions, follower milestones)")
    public ResponseEntity<ApiResponseDTO<String>> sendEmailAlert(
            @Valid @RequestBody EmailAlertRequestDTO request) {
        return ResponseEntity.ok(notificationService.sendEmailAlert(request));
    }

    // ── ADMIN: GET ALL PLATFORM NOTIFICATIONS ──

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all notifications platform-wide (Admin only)",
               description = "Used in admin dashboard for platform analytics")
    public ResponseEntity<ApiResponseDTO<List<NotificationResponseDTO>>> getAll() {
        return ResponseEntity.ok(notificationService.getAll());
    }

    // ── ADMIN: GET BY TYPE ──

    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get notifications by type (Admin only)",
               description = "Filter by LIKE, COMMENT, REPLY, FOLLOW, or MENTION")
    public ResponseEntity<ApiResponseDTO<List<NotificationResponseDTO>>> getByType(
            @PathVariable NotificationType type) {
        return ResponseEntity.ok(notificationService.getByType(type));
    }

    // ── PRIVATE HELPERS ──

    private Integer getRequestingUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("requestingUserId");
        if (userId == null) {
            throw new RuntimeException("Unauthorized: userId not found in request");
        }
        return (Integer) userId;
    }

    private String getRequestingUserRole(HttpServletRequest request) {
        Object role = request.getAttribute("requestingUserRole");
        return role != null ? (String) role : "USER";
    }
}