package com.connectsphere.auth.service;

import com.connectsphere.auth.entity.OtpType;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@connectsphere.com");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
    }

    @Test
    void testSendOtpEmail_Verification() {
        emailService.sendOtpEmail("test@example.com", "123456", OtpType.EMAIL_VERIFICATION);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendOtpEmail_PasswordReset() {
        emailService.sendOtpEmail("test@example.com", "654321", OtpType.PASSWORD_RESET);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendOtpEmail_Exception() {
        // Force an exception during creation or sending
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Mail error"));
        
        // Should not throw exception due to try-catch in service
        emailService.sendOtpEmail("test@example.com", "123456", OtpType.EMAIL_VERIFICATION);
        
        verify(mailSender, times(0)).send(any(MimeMessage.class));
    }
}
