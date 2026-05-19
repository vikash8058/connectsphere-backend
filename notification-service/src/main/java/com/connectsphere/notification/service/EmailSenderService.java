package com.connectsphere.notification.service;

import com.connectsphere.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailSenderService {

    private final JavaMailSender mailSender;

    @Value("${notification.mail.from:noreply@connectsphere.com}")
    private String fromEmail;

    /**
     * Send email alert asynchronously to the recipient.
     * Fire-and-forget — does not block the API response.
     */
    @Async
    public void sendEmailAlertAsync(String toEmail, NotificationType type, String message) {
        try {
            String subject = switch (type) {
                case SYSTEM -> "🔔 System Announcement — ConnectSphere";
                case FOLLOW -> "🎉 Milestone reached! You have a new follower — ConnectSphere";
                case LIKE -> "Someone liked your post on ConnectSphere";
                case COMMENT -> "New comment on your post — ConnectSphere";
                case REPLY -> "Someone replied to your comment — ConnectSphere";
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
