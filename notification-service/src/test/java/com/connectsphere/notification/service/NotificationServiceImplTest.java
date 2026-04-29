package com.connectsphere.notification.service;

import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.exception.NotificationNotFoundException;
import com.connectsphere.notification.exception.UnauthorizedActionException;
import com.connectsphere.notification.repository.NotificationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl Unit Tests")
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService,
                "fromEmail", "noreply@connectsphere.com");
    }

    // ── Test Data Helpers ───

    private Notification buildNotification(Integer id, Integer recipientId, Integer actorId,
                                            NotificationType type, boolean isRead) {
        return Notification.builder()
                .notificationId(id)
                .recipientId(recipientId)
                .actorId(actorId)
                .type(type)
                .message("Test notification message")
                .targetId(10)
                .targetType("POST")
                .deepLinkUrl("/posts/10")
                .isRead(isRead)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private CreateNotificationRequestDTO buildCreateRequest(Integer recipientId,
                                                             Integer actorId,
                                                             NotificationType type) {
        return CreateNotificationRequestDTO.builder()
                .recipientId(recipientId)
                .actorId(actorId)
                .type(type)
                .message("Test message")
                .targetId(10)
                .targetType("POST")
                .deepLinkUrl("/posts/10")
                .build();
    }

    // ── createNotification ───

    @Nested
    @DisplayName("createNotification()")
    class CreateNotificationTests {

        @Test
        @DisplayName("Should create notification successfully")
        void shouldCreateNotification() {
            CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.LIKE);
            Notification saved = buildNotification(1, 2, 1, NotificationType.LIKE, false);

            when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.LIKE))
                    .thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

            ApiResponseDTO<NotificationResponseDTO> response =
                    notificationService.createNotification(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Notification created successfully");
            assertThat(response.getData().getNotificationId()).isEqualTo(1);
            assertThat(response.getData().getIsRead()).isFalse();
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("Should skip duplicate notification for same actor, target, type")
        void shouldSkipDuplicateNotification() {
            CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.LIKE);
            Notification existing = buildNotification(1, 2, 1, NotificationType.LIKE, false);

            when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.LIKE))
                    .thenReturn(Optional.of(existing));

            ApiResponseDTO<NotificationResponseDTO> response =
                    notificationService.createNotification(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("Duplicate");
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should skip self-notification when actorId == recipientId")
        void shouldSkipSelfNotification() {
            CreateNotificationRequestDTO request = buildCreateRequest(1, 1, NotificationType.LIKE);

            ApiResponseDTO<NotificationResponseDTO> response =
                    notificationService.createNotification(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("Self-notification skipped");
            verify(notificationRepository, never()).save(any());
            verify(notificationRepository, never()).findByActorIdAndTargetIdAndType(any(), any(), any());
        }

        @Test
        @DisplayName("Should create FOLLOW notification without targetId (no deduplication check)")
        void shouldCreateFollowNotificationWithoutTargetId() {
            CreateNotificationRequestDTO request = CreateNotificationRequestDTO.builder()
                    .recipientId(2)
                    .actorId(1)
                    .type(NotificationType.FOLLOW)
                    .message("vikash started following you")
                    .deepLinkUrl("/profile/1")
                    .build(); // no targetId

            Notification saved = Notification.builder()
                    .notificationId(5)
                    .recipientId(2)
                    .actorId(1)
                    .type(NotificationType.FOLLOW)
                    .message("vikash started following you")
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

            ApiResponseDTO<NotificationResponseDTO> response =
                    notificationService.createNotification(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getType()).isEqualTo(NotificationType.FOLLOW);
            // deduplication query should NOT be called since targetId is null
            verify(notificationRepository, never())
                    .findByActorIdAndTargetIdAndType(any(), any(), any());
        }
    }

    // ── markAsRead ───

    @Nested
    @DisplayName("markAsRead()")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read for the owner")
        void shouldMarkAsReadForOwner() {
            Notification notification = buildNotification(1, 5, 2, NotificationType.COMMENT, false);
            when(notificationRepository.findByNotificationId(1))
                    .thenReturn(Optional.of(notification));

            ApiResponseDTO<String> response = notificationService.markAsRead(1, 5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Notification marked as read");
            verify(notificationRepository).markAsReadById(1);
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-owner tries to mark as read")
        void shouldThrowForNonOwner() {
            Notification notification = buildNotification(1, 5, 2, NotificationType.COMMENT, false);
            when(notificationRepository.findByNotificationId(1))
                    .thenReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationService.markAsRead(1, 99))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("own notifications");

            verify(notificationRepository, never()).markAsReadById(any());
        }

        @Test
        @DisplayName("Should throw NotificationNotFoundException when notification does not exist")
        void shouldThrowNotificationNotFound() {
            when(notificationRepository.findByNotificationId(999))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(999, 5))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ── markAllRead ───

    @Nested
    @DisplayName("markAllRead()")
    class MarkAllReadTests {

        @Test
        @DisplayName("Should mark all unread notifications as read for recipient")
        void shouldMarkAllRead() {
            ApiResponseDTO<String> response = notificationService.markAllRead(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("All notifications marked as read");
            verify(notificationRepository).markAllAsReadByRecipient(5);
        }
    }

    // ── getByRecipient ────

    @Nested
    @DisplayName("getByRecipient()")
    class GetByRecipientTests {

        @Test
        @DisplayName("Should return all notifications when isRead filter is null")
        void shouldReturnAllWhenIsReadIsNull() {
            List<Notification> list = List.of(
                    buildNotification(1, 5, 2, NotificationType.LIKE, false),
                    buildNotification(2, 5, 3, NotificationType.FOLLOW, true)
            );
            when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(5))
                    .thenReturn(list);

            ApiResponseDTO<List<NotificationResponseDTO>> response =
                    notificationService.getByRecipient(5, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
        }

        @Test
        @DisplayName("Should return only unread notifications when isRead=false")
        void shouldReturnOnlyUnread() {
            List<Notification> unread = List.of(
                    buildNotification(1, 5, 2, NotificationType.LIKE, false)
            );
            when(notificationRepository
                    .findByRecipientIdAndIsReadOrderByCreatedAtDesc(5, false))
                    .thenReturn(unread);

            ApiResponseDTO<List<NotificationResponseDTO>> response =
                    notificationService.getByRecipient(5, false);

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getData().get(0).getIsRead()).isFalse();
        }

        @Test
        @DisplayName("Should return empty list when recipient has no notifications")
        void shouldReturnEmptyList() {
            when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(99))
                    .thenReturn(List.of());

            ApiResponseDTO<List<NotificationResponseDTO>> response =
                    notificationService.getByRecipient(99, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── getUnreadCount ───

    @Nested
    @DisplayName("getUnreadCount()")
    class GetUnreadCountTests {

        @Test
        @DisplayName("Should return correct unread count for badge")
        void shouldReturnUnreadCount() {
            when(notificationRepository.countByRecipientIdAndIsRead(5, false))
                    .thenReturn(3);

            ApiResponseDTO<Integer> response = notificationService.getUnreadCount(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return 0 when all notifications are read")
        void shouldReturnZeroWhenAllRead() {
            when(notificationRepository.countByRecipientIdAndIsRead(5, false))
                    .thenReturn(0);

            ApiResponseDTO<Integer> response = notificationService.getUnreadCount(5);

            assertThat(response.getData()).isEqualTo(0);
        }
    }

    // ── deleteNotification ───

    @Nested
    @DisplayName("deleteNotification()")
    class DeleteNotificationTests {

        @Test
        @DisplayName("Should delete notification for the owner (USER role)")
        void shouldDeleteForOwner() {
            Notification notification = buildNotification(1, 5, 2, NotificationType.LIKE, false);
            when(notificationRepository.findByNotificationId(1))
                    .thenReturn(Optional.of(notification));

            ApiResponseDTO<String> response =
                    notificationService.deleteNotification(1, 5, "USER");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Notification deleted successfully");
            verify(notificationRepository).deleteByNotificationId(1);
        }

        @Test
        @DisplayName("Should allow ADMIN to delete any notification regardless of ownership")
        void shouldAllowAdminToDeleteAny() {
            Notification notification = buildNotification(1, 5, 2, NotificationType.LIKE, false);
            when(notificationRepository.findByNotificationId(1))
                    .thenReturn(Optional.of(notification));

            // userId=99 is not the owner (owner is 5), but role is ADMIN
            ApiResponseDTO<String> response =
                    notificationService.deleteNotification(1, 99, "ADMIN");

            assertThat(response.isSuccess()).isTrue();
            verify(notificationRepository).deleteByNotificationId(1);
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException for non-owner non-admin")
        void shouldThrowForUnauthorizedUser() {
            Notification notification = buildNotification(1, 5, 2, NotificationType.LIKE, false);
            when(notificationRepository.findByNotificationId(1))
                    .thenReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationService.deleteNotification(1, 99, "USER"))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("own notifications");

            verify(notificationRepository, never()).deleteByNotificationId(any());
        }

        @Test
        @DisplayName("Should throw NotificationNotFoundException when notification does not exist")
        void shouldThrowWhenNotFound() {
            when(notificationRepository.findByNotificationId(999))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.deleteNotification(999, 5, "USER"))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ── sendBulkNotification ───
    @Nested
    @DisplayName("sendBulkNotification()")
    class BulkNotificationTests {

        @Test
        @DisplayName("Should save notifications for all provided recipient IDs")
        void shouldSaveBulkNotifications() {
            BulkNotificationRequestDTO request = BulkNotificationRequestDTO.builder()
                    .recipientIds(List.of(1, 2, 3))
                    .message("ConnectSphere platform announcement!")
                    .type("MENTION")
                    .deepLinkUrl("/announcements")
                    .build();

            ApiResponseDTO<String> response = notificationService.sendBulkNotification(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("3 recipients");
            verify(notificationRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Should return early without saving when recipientIds is empty")
        void shouldReturnEarlyForEmptyRecipients() {
            BulkNotificationRequestDTO request = BulkNotificationRequestDTO.builder()
                    .recipientIds(List.of())
                    .message("Test")
                    .type("MENTION")
                    .build();

            ApiResponseDTO<String> response = notificationService.sendBulkNotification(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("No recipients");
            verify(notificationRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Should return early without saving when recipientIds is null")
        void shouldReturnEarlyForNullRecipients() {
            BulkNotificationRequestDTO request = BulkNotificationRequestDTO.builder()
                    .recipientIds(null)
                    .message("Test")
                    .type("MENTION")
                    .build();

            ApiResponseDTO<String> response = notificationService.sendBulkNotification(request);

            assertThat(response.isSuccess()).isTrue();
            verify(notificationRepository, never()).saveAll(anyList());
        }
    }

    // ── sendEmailAlert ───

    @Nested
    @DisplayName("sendEmailAlert()")
    class EmailAlertTests {

        @Test
        @DisplayName("Should send email successfully via JavaMailSender")
        void shouldSendEmailSuccessfully() {
            EmailAlertRequestDTO request = EmailAlertRequestDTO.builder()
                    .toEmail("user@example.com")
                    .subject("You reached 100 followers!")
                    .body("Congratulations! You just hit 100 followers on ConnectSphere.")
                    .build();

            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            ApiResponseDTO<String> response = notificationService.sendEmailAlert(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Email alert sent successfully");
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Should return error response when email sending fails")
        void shouldReturnErrorWhenEmailFails() {
            EmailAlertRequestDTO request = EmailAlertRequestDTO.builder()
                    .toEmail("user@example.com")
                    .subject("Test")
                    .body("Test body")
                    .build();

            doThrow(new RuntimeException("SMTP connection refused"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            ApiResponseDTO<String> response = notificationService.sendEmailAlert(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).contains("Failed to send email");
        }
    }
}