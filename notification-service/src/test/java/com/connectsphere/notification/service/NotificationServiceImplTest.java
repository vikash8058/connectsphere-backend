package com.connectsphere.notification.service;

import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.exception.NotificationNotFoundException;
import com.connectsphere.notification.exception.UnauthorizedActionException;
import com.connectsphere.notification.client.AuthClient;
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

        @Mock
        private AuthClient authClient;

        @Mock
        private EmailSenderService emailSenderService;

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

                        ApiResponseDTO<NotificationResponseDTO> response = notificationService
                                        .createNotification(request);

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

                        ApiResponseDTO<NotificationResponseDTO> response = notificationService
                                        .createNotification(request);

                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getMessage()).contains("Duplicate");
                        verify(notificationRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should skip self-notification when actorId == recipientId")
                void shouldSkipSelfNotification() {
                        CreateNotificationRequestDTO request = buildCreateRequest(1, 1, NotificationType.LIKE);

                        ApiResponseDTO<NotificationResponseDTO> response = notificationService
                                        .createNotification(request);

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

                        ApiResponseDTO<NotificationResponseDTO> response = notificationService
                                        .createNotification(request);

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
                                        buildNotification(2, 5, 3, NotificationType.FOLLOW, true));
                        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(5))
                                        .thenReturn(list);

                        ApiResponseDTO<List<NotificationResponseDTO>> response = notificationService.getByRecipient(5,
                                        null);

                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData()).hasSize(2);
                }

                @Test
                @DisplayName("Should return only unread notifications when isRead=false")
                void shouldReturnOnlyUnread() {
                        List<Notification> unread = List.of(
                                        buildNotification(1, 5, 2, NotificationType.LIKE, false));
                        when(notificationRepository
                                        .findByRecipientIdAndIsReadOrderByCreatedAtDesc(5, false))
                                        .thenReturn(unread);

                        ApiResponseDTO<List<NotificationResponseDTO>> response = notificationService.getByRecipient(5,
                                        false);

                        assertThat(response.getData()).hasSize(1);
                        assertThat(response.getData().get(0).getIsRead()).isFalse();
                }

                @Test
                @DisplayName("Should return empty list when recipient has no notifications")
                void shouldReturnEmptyList() {
                        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(99))
                                        .thenReturn(List.of());

                        ApiResponseDTO<List<NotificationResponseDTO>> response = notificationService.getByRecipient(99,
                                        null);

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

                        ApiResponseDTO<String> response = notificationService.deleteNotification(1, 5, "USER");

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
                        ApiResponseDTO<String> response = notificationService.deleteNotification(1, 99, "ADMIN");

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

        // ── createNotification with AuthClient enrichment ───

        @Nested
        @DisplayName("createNotification() with AuthClient")
        class CreateNotificationWithAuthTests {

                @Test
                @DisplayName("Should use actor fullName from AuthClient")
                void shouldUseActorFullName() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.LIKE);
                        request.setMessage(null);

                        UserDataDTO actorData = new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "john", "John Doe", "john@test.com", null));
                        when(authClient.getUserById(1)).thenReturn(actorData);
                        UserDataDTO recipientData = new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "jane", "Jane", "jane@test.com", null));
                        when(authClient.getUserById(2)).thenReturn(recipientData);

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.LIKE))
                                        .thenReturn(Optional.empty());
                        Notification saved = buildNotification(1, 2, 1, NotificationType.LIKE, false);
                        when(notificationRepository.save(any())).thenReturn(saved);

                        ApiResponseDTO<NotificationResponseDTO> resp = notificationService.createNotification(request);
                        assertThat(resp.isSuccess()).isTrue();
                }

                @Test
                @DisplayName("Should fallback to username when fullName is null")
                void shouldFallbackToUsername() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.COMMENT);
                        request.setMessage(null);

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "john", null, "john@test.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "jane", "Jane", "jane@test.com", null)));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.COMMENT))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.COMMENT, false));

                        ApiResponseDTO<NotificationResponseDTO> resp = notificationService.createNotification(request);
                        assertThat(resp.isSuccess()).isTrue();
                }

                @Test
                @DisplayName("Should handle AuthClient failure gracefully for actor")
                void shouldHandleActorFetchFailure() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.REPLY);
                        request.setMessage(null);

                        when(authClient.getUserById(1)).thenThrow(new RuntimeException("Feign down"));
                        when(authClient.getUserById(2)).thenReturn(null);

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.REPLY))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.REPLY, false));

                        ApiResponseDTO<NotificationResponseDTO> resp = notificationService.createNotification(request);
                        assertThat(resp.isSuccess()).isTrue();
                }

                @Test
                @DisplayName("Should handle null AuthClient response for recipient")
                void shouldHandleNullRecipientData() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.MENTION);
                        request.setMessage(null);

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "john", "John", "j@b.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true, null));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.MENTION))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.MENTION, false));

                        ApiResponseDTO<NotificationResponseDTO> resp = notificationService.createNotification(request);
                        assertThat(resp.isSuccess()).isTrue();
                }

                @Test
                @DisplayName("Should send email for SYSTEM type (high priority)")
                void shouldSendEmailForSystemType() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.SYSTEM);
                        request.setMessage("System alert");

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "admin", "Admin", "a@b.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "user", "User", "user@test.com", null)));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.SYSTEM))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.SYSTEM, false));

                        notificationService.createNotification(request);
                        verify(emailSenderService).sendEmailAlertAsync(anyString(), any(), anyString());
                }

                @Test
                @DisplayName("Should send email for PAYMENT_SUCCESS type")
                void shouldSendEmailForPaymentSuccess() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1,
                                        NotificationType.PAYMENT_SUCCESS);
                        request.setMessage(null);

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "sys", "System", "s@b.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "user", "User", "user@test.com", null)));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10,
                                        NotificationType.PAYMENT_SUCCESS))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.PAYMENT_SUCCESS, false));

                        notificationService.createNotification(request);
                        verify(emailSenderService).sendEmailAlertAsync(anyString(), any(), anyString());
                }

                @Test
                @DisplayName("Should send email for SUBSCRIPTION_EXPIRY type")
                void shouldSendEmailForSubscriptionExpiry() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1,
                                        NotificationType.SUBSCRIPTION_EXPIRY);
                        request.setMessage(null);

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "sys", "System", "s@b.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "user", "User", "user@test.com", null)));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10,
                                        NotificationType.SUBSCRIPTION_EXPIRY))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.SUBSCRIPTION_EXPIRY, false));

                        notificationService.createNotification(request);
                        verify(emailSenderService).sendEmailAlertAsync(anyString(), any(), anyString());
                }

                @Test
                @DisplayName("Should NOT send email for LIKE type (low priority)")
                void shouldNotSendEmailForLikeType() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.LIKE);

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "john", "John", "j@b.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "user", "User", "user@test.com", null)));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.LIKE))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.LIKE, false));

                        notificationService.createNotification(request);
                        verify(emailSenderService, never()).sendEmailAlertAsync(anyString(), any(), anyString());
                }

                @Test
                @DisplayName("Should check follower milestone for FOLLOW type")
                void shouldCheckFollowerMilestone() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.FOLLOW);
                        request.setMessage(null);

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "john", "John", "j@b.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "user", "User", "user@test.com", null)));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.FOLLOW))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.FOLLOW, false));
                        when(notificationRepository.countByRecipientIdAndType(2, NotificationType.FOLLOW))
                                        .thenReturn(100L);

                        notificationService.createNotification(request);
                        verify(emailSenderService).sendEmailAlertAsync(anyString(), any(), anyString());
                }

                @Test
                @DisplayName("Should NOT email for FOLLOW when not at milestone")
                void shouldNotEmailForNonMilestoneFollow() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.FOLLOW);
                        request.setMessage(null);

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "john", "John", "j@b.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "user", "User", "user@test.com", null)));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.FOLLOW))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.FOLLOW, false));
                        when(notificationRepository.countByRecipientIdAndType(2, NotificationType.FOLLOW))
                                        .thenReturn(50L);

                        notificationService.createNotification(request);
                        verify(emailSenderService, never()).sendEmailAlertAsync(anyString(), any(), anyString());
                }

                @Test
                @DisplayName("Should handle milestone check exception gracefully")
                void shouldHandleMilestoneCheckException() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.FOLLOW);
                        request.setMessage(null);

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "john", "John", "j@b.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "user", "User", "user@test.com", null)));

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.FOLLOW))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.FOLLOW, false));
                        when(notificationRepository.countByRecipientIdAndType(2, NotificationType.FOLLOW))
                                        .thenThrow(new RuntimeException("DB error"));

                        notificationService.createNotification(request);
                        verify(emailSenderService, never()).sendEmailAlertAsync(anyString(), any(), anyString());
                }

                @Test
                @DisplayName("Should use existing message when not starting with Someone")
                void shouldUseExistingNonGenericMessage() {
                        CreateNotificationRequestDTO request = buildCreateRequest(2, 1, NotificationType.LIKE);
                        request.setMessage("Custom message from service");

                        when(authClient.getUserById(1)).thenReturn(null);

                        when(notificationRepository.findByActorIdAndTargetIdAndType(1, 10, NotificationType.LIKE))
                                        .thenReturn(Optional.empty());
                        when(notificationRepository.save(any())).thenReturn(
                                        buildNotification(1, 2, 1, NotificationType.LIKE, false));

                        ApiResponseDTO<NotificationResponseDTO> resp = notificationService.createNotification(request);
                        assertThat(resp.isSuccess()).isTrue();
                }


        }

        // ── Bulk SYSTEM email path ───

        @Nested
        @DisplayName("sendBulkNotification() with SYSTEM emails")
        class BulkSystemEmailTests {

                @Test
                @DisplayName("Should send emails for SYSTEM type bulk notification")
                void shouldSendEmailsForSystemBulk() {
                        BulkNotificationRequestDTO request = BulkNotificationRequestDTO.builder()
                                        .recipientIds(List.of(1, 2))
                                        .message("System broadcast")
                                        .type("SYSTEM")
                                        .deepLinkUrl("/announcements")
                                        .build();

                        when(authClient.getUserById(1)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(1, "u1", "User1", "u1@test.com", null)));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "u2", "User2", "u2@test.com", null)));

                        notificationService.sendBulkNotification(request);
                        verify(emailSenderService, times(2)).sendEmailAlertAsync(anyString(), any(), anyString());
                }

                @Test
                @DisplayName("Should handle email failure during bulk SYSTEM gracefully")
                void shouldHandleBulkEmailFailure() {
                        BulkNotificationRequestDTO request = BulkNotificationRequestDTO.builder()
                                        .recipientIds(List.of(1))
                                        .message("Broadcast")
                                        .type("SYSTEM")
                                        .build();

                        when(authClient.getUserById(1)).thenThrow(new RuntimeException("Feign down"));

                        notificationService.sendBulkNotification(request);
                        verify(emailSenderService, never()).sendEmailAlertAsync(anyString(), any(), anyString());
                }
        }

        // ── getAll / getByType ───

        @Nested
        @DisplayName("getAll() and getByType()")
        class GetAllAndByTypeTests {

                @Test
                @DisplayName("Should return all notifications for admin")
                void shouldGetAll() {
                        when(notificationRepository.findAll()).thenReturn(
                                        List.of(buildNotification(1, 2, 1, NotificationType.LIKE, false)));

                        ApiResponseDTO<List<NotificationResponseDTO>> resp = notificationService.getAll();
                        assertThat(resp.isSuccess()).isTrue();
                        assertThat(resp.getData()).hasSize(1);
                }

                @Test
                @DisplayName("Should return notifications filtered by type")
                void shouldGetByType() {
                        when(notificationRepository.findByTypeOrderByCreatedAtDesc(NotificationType.FOLLOW))
                                        .thenReturn(List.of(
                                                        buildNotification(1, 2, 1, NotificationType.FOLLOW, false)));

                        ApiResponseDTO<List<NotificationResponseDTO>> resp = notificationService
                                        .getByType(NotificationType.FOLLOW);
                        assertThat(resp.isSuccess()).isTrue();
                        assertThat(resp.getData()).hasSize(1);
                }
        }

        // ── enrichWithActorInfo ───

        @Nested
        @DisplayName("enrichWithActorInfo() via getByRecipient")
        class EnrichActorInfoTests {

                @Test
                @DisplayName("Should replace Someone in message with real actor name")
                void shouldReplaceGenericMessage() {
                        Notification n = buildNotification(1, 5, 2, NotificationType.LIKE, false);
                        n.setMessage("Someone liked your post");
                        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(5))
                                        .thenReturn(List.of(n));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "john", "John Doe", "j@b.com", "pic.jpg")));

                        ApiResponseDTO<List<NotificationResponseDTO>> resp = notificationService.getByRecipient(5,
                                        null);
                        assertThat(resp.getData().get(0).getActorUsername()).isEqualTo("john");
                        assertThat(resp.getData().get(0).getActorProfilePic()).isEqualTo("pic.jpg");
                        assertThat(resp.getData().get(0).getMessage()).contains("John Doe");
                }

                @Test
                @DisplayName("Should use username when fullName is null during enrichment")
                void shouldUseUsernameWhenFullNameNull() {
                        Notification n = buildNotification(1, 5, 2, NotificationType.LIKE, false);
                        n.setMessage("Someone liked your post");
                        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(5))
                                        .thenReturn(List.of(n));
                        when(authClient.getUserById(2)).thenReturn(new UserDataDTO(true,
                                        new UserDataDTO.UserDTO(2, "john", null, "j@b.com", null)));

                        ApiResponseDTO<List<NotificationResponseDTO>> resp = notificationService.getByRecipient(5,
                                        null);
                        assertThat(resp.getData().get(0).getMessage()).contains("john");
                }

                @Test
                @DisplayName("Should handle AuthClient failure during enrichment")
                void shouldHandleEnrichmentFailure() {
                        Notification n = buildNotification(1, 5, 2, NotificationType.LIKE, false);
                        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(5))
                                        .thenReturn(List.of(n));
                        when(authClient.getUserById(2)).thenThrow(new RuntimeException("Feign down"));

                        ApiResponseDTO<List<NotificationResponseDTO>> resp = notificationService.getByRecipient(5,
                                        null);
                        assertThat(resp.isSuccess()).isTrue();
                }

                @Test
                @DisplayName("Should skip enrichment for actorId 0 (system)")
                void shouldSkipEnrichmentForSystemActor() {
                        Notification n = buildNotification(1, 5, 0, NotificationType.SYSTEM, false);
                        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(5))
                                        .thenReturn(List.of(n));

                        ApiResponseDTO<List<NotificationResponseDTO>> resp = notificationService.getByRecipient(5,
                                        null);
                        assertThat(resp.isSuccess()).isTrue();
                        assertThat(resp.getData().get(0).getActorUsername()).isEqualTo("ConnectSphere");
                }
        }
}