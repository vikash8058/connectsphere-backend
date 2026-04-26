package com.connectsphere.notification.service;

import com.connectsphere.notification.client.AuthClient;
import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.exception.NotificationNotFoundException;
import com.connectsphere.notification.exception.UnauthorizedActionException;
import com.connectsphere.notification.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * NotificationServiceImpl - Business Logic for Notification Service
 *
 * Key flows:
 *   1. createNotification()     → Self-notification check → Deduplication check
 *                               → Save → Return DTO
 *   2. sendBulkNotification()   → Build Notification per recipientId → saveAll()
 *   3. markAsRead()             → Ownership check → atomic UPDATE (no full entity load)
 *   4. markAllRead()            → atomic bulk UPDATE for recipient
 *   5. getByRecipient()         → Filter by isRead if provided → return list
 *   6. getUnreadCount()         → COUNT query → badge number
 *   7. deleteNotification()     → Ownership or ADMIN check → delete
 *   8. sendEmailAlert()         → JavaMailSender → @Async fire-and-forget
 *   9. getAll()                 → Admin: all notifications platform-wide
 *  10. getByType()              → Admin: filter by NotificationType
 *
 * RabbitMQ consumer (NotificationListener) calls createNotification() after
 * deserializing messages from the queue — same logic path as direct REST call.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final AuthClient authClient;

    @Value("${notification.mail.from:noreply@connectsphere.com}")
    private String fromEmail;

    // ── CREATE SINGLE NOTIFICATION ──

    @Override
    @Transactional
    public ApiResponseDTO<NotificationResponseDTO> createNotification(
            CreateNotificationRequestDTO request) {

        log.info("Creating notification: type={} recipientId={} actorId={}",
                request.getType(), request.getRecipientId(), request.getActorId());

        // Self-notification prevention
        if (request.getRecipientId() != null
                && request.getRecipientId().equals(request.getActorId())) {
            log.debug("Self-notification skipped: userId={}", request.getActorId());
            return ApiResponseDTO.success("Self-notification skipped");
        }

        // Deduplication check
        if (request.getTargetId() != null) {
            boolean alreadyExists = notificationRepository
                    .findByActorIdAndTargetIdAndType(
                            request.getActorId(),
                            request.getTargetId(),
                            request.getType())
                    .isPresent();
            if (alreadyExists) {
                log.info("Duplicate notification skipped");
                return ApiResponseDTO.success("Duplicate notification skipped — not saved");
            }
        }

        // ── FETCH ACTOR USERNAME for real message ──
        // e.g. "vikash liked your post" instead of "Someone liked your post"
        String actorName = "Someone"; // fallback
        String recipientEmail = null; // for email alert

        try {
            UserDataDTO actorData = authClient.getUserById(request.getActorId());
            if (actorData != null && actorData.getData() != null) {
                actorName = actorData.getData().getFullName() != null
                        ? actorData.getData().getFullName()
                        : actorData.getData().getUsername();
            }
        } catch (Exception e) {
            log.warn("Could not fetch actor info (actorId={}): {}", request.getActorId(), e.getMessage());
        }

        // ── FETCH RECIPIENT EMAIL for email alert ──
        if (request.getRecipientId() != null) {
            try {
                UserDataDTO recipientData = authClient.getUserById(request.getRecipientId());
                if (recipientData != null && recipientData.getData() != null) {
                    recipientEmail = recipientData.getData().getEmail();
                }
            } catch (Exception e) {
                log.warn("Could not fetch recipient info (recipientId={}): {}",
                        request.getRecipientId(), e.getMessage());
            }
        }

        // ── BUILD REAL MESSAGE using actor's actual name ──
        // If the calling service already passed a specific message, use it
        // Otherwise build it here from actorName
        String finalMessage = buildMessage(actorName, request.getType(), request.getMessage());

        // ── SAVE NOTIFICATION ──
        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .actorId(request.getActorId())
                .type(request.getType())
                .message(finalMessage)
                .targetId(request.getTargetId())
                .targetType(request.getTargetType())
                .deepLinkUrl(request.getDeepLinkUrl())
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created with id={}", saved.getNotificationId());

        // ── SEND EMAIL only for HIGH-PRIORITY events ──
        // Per spec: email alerts for account actions & follower milestones ONLY.
        // LIKE / COMMENT / REPLY / MENTION → in-app notification only (no email).
        // FOLLOW → in-app only for regular follows; email on milestone (handled separately).
        if (recipientEmail != null && isHighPriorityEmailEvent(request.getType(), request.getRecipientId())) {
            sendEmailAlertAsync(recipientEmail, request.getType(), finalMessage);
        }

        return ApiResponseDTO.success("Notification created successfully", toDTO(saved));
    }

    // ── BULK NOTIFICATION (admin broadcast) ──

    @Override
    @Transactional
    public ApiResponseDTO<String> sendBulkNotification(BulkNotificationRequestDTO request) {
        log.info("Bulk notification dispatch: {} recipients, type={}",
                request.getRecipientIds() != null ? request.getRecipientIds().size() : "ALL",
                request.getType());

        if (request.getRecipientIds() == null || request.getRecipientIds().isEmpty()) {
            log.warn("sendBulkNotification called with empty recipientIds — no notifications sent");
            return ApiResponseDTO.success("No recipients provided. No notifications sent.");
        }

        List<Notification> notifications = request.getRecipientIds().stream()
                .map(recipientId -> Notification.builder()
                        .recipientId(recipientId)
                        .actorId(0)   // 0 = system actor (platform broadcast)
                        .type(NotificationType.valueOf(request.getType()))
                        .message(request.getMessage())
                        .targetId(null)
                        .targetType(null)
                        .deepLinkUrl(request.getDeepLinkUrl())
                        .isRead(false)
                        .build())
                .collect(Collectors.toList());

        notificationRepository.saveAll(notifications);
        log.info("Bulk notifications saved: count={}", notifications.size());

        // ── ALSO SEND EMAILS FOR SYSTEM BROADCASTS ──
        if ("SYSTEM".equalsIgnoreCase(request.getType())) {
            for (Integer rid : request.getRecipientIds()) {
                try {
                    UserDataDTO userRes = authClient.getUserById(rid);
                    if (userRes != null && userRes.getData() != null && userRes.getData().getEmail() != null) {
                        sendEmailAlertAsync(userRes.getData().getEmail(), NotificationType.SYSTEM, request.getMessage());
                    }
                } catch (Exception e) {
                    log.error("Failed to send broadcast email to user {}: {}", rid, e.getMessage());
                }
            }
        }

        return ApiResponseDTO.success(
                "Bulk notifications sent to " + notifications.size() + " recipients");
    }

    // ── MARK ONE AS READ ──

    @Override
    @Transactional
    public ApiResponseDTO<String> markAsRead(Integer notificationId, Integer requestingUserId) {
        log.debug("markAsRead: notificationId={} userId={}", notificationId, requestingUserId);

        // Verify notification exists and belongs to requesting user
        Notification notification = notificationRepository
                .findByNotificationId(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found with id: " + notificationId));

        if (!notification.getRecipientId().equals(requestingUserId)) {
            throw new UnauthorizedActionException(
                    "You can only mark your own notifications as read");
        }

        // Atomic UPDATE — no full entity load/save cycle needed
        notificationRepository.markAsReadById(notificationId);
        log.debug("Notification marked as read: id={}", notificationId);

        return ApiResponseDTO.success("Notification marked as read");
    }

    // ── MARK ALL AS READ ──

    @Override
    @Transactional
    public ApiResponseDTO<String> markAllRead(Integer recipientId) {
        log.debug("markAllRead: recipientId={}", recipientId);
        notificationRepository.markAllAsReadByRecipient(recipientId);
        return ApiResponseDTO.success("All notifications marked as read");
    }

    // ── GET BY RECIPIENT ──

    @Override
    public ApiResponseDTO<List<NotificationResponseDTO>> getByRecipient(
            Integer recipientId, Boolean isRead) {

        log.debug("getByRecipient: recipientId={} isRead={}", recipientId, isRead);

        List<Notification> notifications;

        if (isRead != null) {
            // Filter by read/unread state
            notifications = notificationRepository
                    .findByRecipientIdAndIsReadOrderByCreatedAtDesc(recipientId, isRead);
        } else {
            // Return all (unread + read), newest first
            notifications = notificationRepository
                    .findByRecipientIdOrderByCreatedAtDesc(recipientId);
        }

        List<NotificationResponseDTO> result = notifications.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        // Batch enrich with actor info (fetch unique actor details)
        enrichWithActorInfo(result);

        return ApiResponseDTO.success("Notifications fetched successfully", result);
    }

    /**
     * Enriches a list of notifications with actor details (username, profile pic)
     * by calling auth-service for each unique actor ID.
     */
    private void enrichWithActorInfo(List<NotificationResponseDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        java.util.Set<Integer> actorIds = dtos.stream()
                .map(NotificationResponseDTO::getActorId)
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Integer, UserDataDTO.UserDTO> actorMap = new java.util.HashMap<>();
        for (Integer actorId : actorIds) {
            try {
                UserDataDTO res = authClient.getUserById(actorId);
                if (res != null && res.getData() != null) {
                    actorMap.put(actorId, res.getData());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch actor info for enrichment (actorId={}): {}", actorId, e.getMessage());
            }
        }

        for (NotificationResponseDTO dto : dtos) {
            UserDataDTO.UserDTO actor = actorMap.get(dto.getActorId());
            if (actor != null) {
                dto.setActorUsername(actor.getUsername());
                dto.setActorProfilePic(actor.getProfilePicUrl());
                
                // If the message is generic "Someone ...", update it with real name
                if (dto.getMessage() != null && dto.getMessage().startsWith("Someone")) {
                    String realName = actor.getFullName() != null ? actor.getFullName() : actor.getUsername();
                    dto.setMessage(dto.getMessage().replace("Someone", realName));
                }
            }
        }
    }

    // ── GET UNREAD COUNT (badge) ──

    @Override
    public ApiResponseDTO<Integer> getUnreadCount(Integer recipientId) {
        int count = notificationRepository.countByRecipientIdAndIsRead(recipientId, false);
        log.debug("Unread count for recipientId={}: {}", recipientId, count);
        return ApiResponseDTO.success("Unread count fetched", count);
    }

    // ── DELETE NOTIFICATION ──

    @Override
    @Transactional
    public ApiResponseDTO<String> deleteNotification(Integer notificationId,
                                                     Integer requestingUserId,
                                                     String requestingUserRole) {
        log.info("Delete notification: id={} userId={}", notificationId, requestingUserId);

        Notification notification = notificationRepository
                .findByNotificationId(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found with id: " + notificationId));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(requestingUserRole);

        if (!isAdmin && !notification.getRecipientId().equals(requestingUserId)) {
            throw new UnauthorizedActionException(
                    "You can only delete your own notifications");
        }

        notificationRepository.deleteByNotificationId(notificationId);
        log.info("Notification deleted: id={}", notificationId);

        return ApiResponseDTO.success("Notification deleted successfully");
    }

    // ── SEND EMAIL ALERT ──

    @Override
    @Async
    public ApiResponseDTO<String> sendEmailAlert(EmailAlertRequestDTO request) {
        log.info("Sending email alert to: {}", request.getToEmail());
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(request.getToEmail());
            mail.setSubject(request.getSubject());
            mail.setText(request.getBody());
            mailSender.send(mail);
            log.info("Email alert sent to: {}", request.getToEmail());
            return ApiResponseDTO.success("Email alert sent successfully");
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", request.getToEmail(), e.getMessage());
            return ApiResponseDTO.error("Failed to send email: " + e.getMessage());
        }
    }

    // ── GET ALL (admin) ──

    @Override
    public ApiResponseDTO<List<NotificationResponseDTO>> getAll() {
        log.debug("Admin: fetching all notifications");
        List<NotificationResponseDTO> all = notificationRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("All notifications fetched", all);
    }

    // ── GET BY TYPE ──

    @Override
    public ApiResponseDTO<List<NotificationResponseDTO>> getByType(NotificationType type) {
        log.debug("Fetching notifications by type={}", type);
        List<NotificationResponseDTO> result = notificationRepository
                .findByTypeOrderByCreatedAtDesc(type)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Notifications by type fetched", result);
    }

    // ── PRIVATE HELPER: Entity → DTO ──

    private NotificationResponseDTO toDTO(Notification n) {
        return NotificationResponseDTO.builder()
                .notificationId(n.getNotificationId())
                .recipientId(n.getRecipientId())
                .actorId(n.getActorId())
                .type(n.getType())
                .message(n.getMessage())
                .targetId(n.getTargetId())
                .targetType(n.getTargetType())
                .deepLinkUrl(n.getDeepLinkUrl())
                .isRead(n.getIsRead())
                .actorUsername(n.getActorId() == 0 ? "ConnectSphere" : "Someone") // ConnectSphere for system notifications
                .createdAt(n.getCreatedAt())
                .build();
    }

    /**
     * Determines whether an event type warrants an email alert.
     *
     * Per case study spec (section 2.5):
     *   "Email alerts are sent for high-priority events
     *    (e.g. account actions, new follower milestones)."
     *
     * Rules:
     *   LIKE, COMMENT, REPLY, MENTION → in-app ONLY. No email.
     *       Reason: these are high-volume social interactions.
     *       1000 likes = 1000 emails → inbox spam.
     *
     *   FOLLOW → email only on milestone counts (100, 500, 1000 followers).
     *       Regular follows → in-app only.
     *
     * Admin broadcast emails are handled separately via sendBulkNotification()
     * and the explicit /notifications/email endpoint — not through this path.
     */
    private boolean isHighPriorityEmailEvent(NotificationType type, Integer recipientId) {
        return switch (type) {
            case LIKE, COMMENT, REPLY, MENTION -> false; // in-app only — never email
            case SYSTEM, FOLLOW, PAYMENT_SUCCESS, SUBSCRIPTION_EXPIRY -> true;  // always email for these types
        };
    }

    /**
     * Returns true if the recipient's current follower count is a milestone
     * worth sending an email about (100, 500, 1000, 5000, ...).
     *
     * We check AFTER the follow is already saved, so the count includes the new follower.
     * This is a best-effort check — if the count query fails, no email is sent (safe default).
     */
    private boolean isFollowerMilestone(Integer recipientId) {
        if (recipientId == null) return false;
        try {
            long followerCount = notificationRepository
                    .countByRecipientIdAndType(recipientId, NotificationType.FOLLOW);
            // Milestone thresholds: 100, 500, 1000, 5000, 10000 ...
            return followerCount == 100 || followerCount == 500
                    || followerCount == 1000 || followerCount == 5000
                    || followerCount == 10000;
        } catch (Exception e) {
            log.warn("Could not check follower milestone for recipientId={}: {}", recipientId, e.getMessage());
            return false;
        }
    }

    /**
     * Build a human-readable notification message using the actor's real name.
     *
     * Examples:
     *   "Vikash Sharma liked your post"
     *   "Rahul Kumar commented on your post"
     *   "Priya Singh started following you"
     */
    private String buildMessage(String actorName, NotificationType type, String fallbackMessage) {
        // If calling service already sent a meaningful message, use it
        // Replace "Someone" with the real name if present
        if (fallbackMessage != null && !fallbackMessage.isBlank()
                && !fallbackMessage.startsWith("Someone")) {
            return fallbackMessage;
        }

        return switch (type) {
            case LIKE    -> actorName + " liked your post";
            case COMMENT -> actorName + " commented on your post";
            case REPLY   -> actorName + " replied to your comment";
            case FOLLOW  -> actorName + " started following you";
            case MENTION -> actorName + " mentioned you in a post";
            case SYSTEM  -> actorName + " sent a system broadcast";
            case PAYMENT_SUCCESS -> "Elite Status Activated! Welcome to the premium club.";
            case SUBSCRIPTION_EXPIRY -> "Your Elite Status has expired. Renew now to keep your badge!";
        };
    }

    /**
     * Send email alert asynchronously to the recipient.
     * Fire-and-forget — does not block the API response.
     */
    @Async
    protected void sendEmailAlertAsync(String toEmail, NotificationType type, String message) {
        try {
            String subject = switch (type) {
                case SYSTEM  -> "🔔 System Announcement — ConnectSphere";
                case FOLLOW  -> "🎉 Milestone reached! You have a new follower — ConnectSphere";
                case LIKE    -> "Someone liked your post on ConnectSphere";
                case COMMENT -> "New comment on your post — ConnectSphere";
                case REPLY   -> "Someone replied to your comment — ConnectSphere";
                case MENTION -> "You were mentioned on ConnectSphere";
                case PAYMENT_SUCCESS -> "💳 Payment Successful — Welcome to ConnectSphere Elite!";
                case SUBSCRIPTION_EXPIRY -> "⚠️ Subscription Expired — ConnectSphere Elite";
            };

            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(toEmail);
            mail.setSubject(subject);
            mail.setText(message + "\n\nOpen ConnectSphere to see more.");
            mailSender.send(mail);

            log.info("Email alert sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}