package com.connectsphere.auth.service;

import com.connectsphere.auth.entity.OtpType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * EmailService - Handles all email communications for ConnectSphere
 *
 * Sends HTML emails asynchronously (non-blocking) via SMTP.
 * Supports EMAIL_VERIFICATION and PASSWORD_RESET OTP templates.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otpCode, OtpType otpType) {
        try {
            String subject;
            String html;
            if (otpType == OtpType.EMAIL_VERIFICATION) {
                subject = "ConnectSphere – Verify Your Email";
                html = buildVerificationTemplate(otpCode);
            } else {
                subject = "ConnectSphere – Password Reset OTP";
                html = buildPasswordResetTemplate(otpCode);
            }
            sendHtml(toEmail, subject, html);
            log.info("OTP email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    private void sendHtml(String to, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }

    private String buildVerificationTemplate(String otpCode) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px;">
                  <div style="max-width:600px;margin:auto;background:#fff;padding:30px;border-radius:10px;">
                    <h2 style="color:#2c3e50;">Welcome to ConnectSphere! 🌐</h2>
                    <p>Thank you for joining. Verify your email with the OTP below:</p>
                    <div style="text-align:center;margin:30px 0;">
                      <span style="font-size:36px;font-weight:bold;color:#3498db;
                                   letter-spacing:8px;padding:15px 30px;
                                   background:#eaf4fb;border-radius:8px;">%s</span>
                    </div>
                    <p style="color:#e74c3c;">⏰ This OTP expires in <strong>10 minutes</strong>.</p>
                    <p style="color:#7f8c8d;font-size:12px;">If you did not sign up, ignore this email.</p>
                    <hr style="border:none;border-top:1px solid #eee;">
                    <p style="color:#7f8c8d;font-size:12px;">
                      © 2026 ConnectSphere — Share Moments. Build Connections. Inspire Communities.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(otpCode);
    }

    private String buildPasswordResetTemplate(String otpCode) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px;">
                  <div style="max-width:600px;margin:auto;background:#fff;padding:30px;border-radius:10px;">
                    <h2 style="color:#e74c3c;">🔐 Password Reset Request</h2>
                    <p>Use the OTP below to reset your ConnectSphere password:</p>
                    <div style="text-align:center;margin:30px 0;">
                      <span style="font-size:36px;font-weight:bold;color:#e74c3c;
                                   letter-spacing:8px;padding:15px 30px;
                                   background:#fdf2f2;border-radius:8px;">%s</span>
                    </div>
                    <p style="color:#e74c3c;">⏰ Expires in <strong>10 minutes</strong>.</p>
                    <p style="color:#7f8c8d;font-size:12px;">
                      If you did not request this, please secure your account immediately.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(otpCode);
    }
}