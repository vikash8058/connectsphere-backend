package com.connectsphere.notification.service;

import com.connectsphere.notification.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailSenderService Unit Tests")
class EmailSenderServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailSenderService emailSenderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailSenderService, "fromEmail", "noreply@connectsphere.com");
    }

    @Test
    @DisplayName("Should successfully send email for SYSTEM notification type")
    void shouldSendSystemEmail() {
        String to = "test@example.com";
        String msg = "System update";

        emailSenderService.sendEmailAlertAsync(to, NotificationType.SYSTEM, msg);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMail = captor.getValue();
        assertThat(sentMail.getFrom()).isEqualTo("noreply@connectsphere.com");
        assertThat(sentMail.getTo()).containsExactly(to);
        assertThat(sentMail.getSubject()).isEqualTo("🔔 System Announcement — ConnectSphere");
        assertThat(sentMail.getText()).contains(msg);
        assertThat(sentMail.getText()).contains("Open ConnectSphere to see more.");
    }

    @Test
    @DisplayName("Should successfully send email for FOLLOW notification type")
    void shouldSendFollowEmail() {
        String to = "follower@example.com";
        String msg = "You got a new follower";

        emailSenderService.sendEmailAlertAsync(to, NotificationType.FOLLOW, msg);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMail = captor.getValue();
        assertThat(sentMail.getSubject()).isEqualTo("🎉 Milestone reached! You have a new follower — ConnectSphere");
    }

    @Test
    @DisplayName("Should successfully send email for LIKE notification type")
    void shouldSendLikeEmail() {
        String to = "like@example.com";
        emailSenderService.sendEmailAlertAsync(to, NotificationType.LIKE, "Liked!");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Someone liked your post on ConnectSphere");
    }

    @Test
    @DisplayName("Should successfully send email for COMMENT notification type")
    void shouldSendCommentEmail() {
        String to = "comment@example.com";
        emailSenderService.sendEmailAlertAsync(to, NotificationType.COMMENT, "Commented!");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("New comment on your post — ConnectSphere");
    }

    @Test
    @DisplayName("Should successfully send email for REPLY notification type")
    void shouldSendReplyEmail() {
        String to = "reply@example.com";
        emailSenderService.sendEmailAlertAsync(to, NotificationType.REPLY, "Replied!");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Someone replied to your comment — ConnectSphere");
    }

    @Test
    @DisplayName("Should successfully send email for MENTION notification type")
    void shouldSendMentionEmail() {
        String to = "mention@example.com";
        emailSenderService.sendEmailAlertAsync(to, NotificationType.MENTION, "Mentioned!");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("You were mentioned on ConnectSphere");
    }

    @Test
    @DisplayName("Should successfully send email for PAYMENT_SUCCESS notification type")
    void shouldSendPaymentSuccessEmail() {
        String to = "pay@example.com";
        emailSenderService.sendEmailAlertAsync(to, NotificationType.PAYMENT_SUCCESS, "Paid!");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("💳 Payment Successful — Welcome to ConnectSphere Elite!");
    }

    @Test
    @DisplayName("Should successfully send email for SUBSCRIPTION_EXPIRY notification type")
    void shouldSendSubscriptionExpiryEmail() {
        String to = "expiry@example.com";
        emailSenderService.sendEmailAlertAsync(to, NotificationType.SUBSCRIPTION_EXPIRY, "Expired!");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("⚠️ Subscription Expired — ConnectSphere Elite");
    }

    @Test
    @DisplayName("Should swallow exceptions gracefully when JavaMailSender fails")
    void shouldHandleMailSenderExceptionGracefully() {
        String to = "fail@example.com";
        doThrow(new RuntimeException("SMTP Server Down")).when(mailSender).send(any(SimpleMailMessage.class));

        // This call should not throw an exception as EmailSenderService catches all exceptions
        emailSenderService.sendEmailAlertAsync(to, NotificationType.SYSTEM, "Should not crash");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
