package com.connectsphere.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * EmailAlertRequestDTO - Payload for sending an email alert
 *
 * From case study section 4.6:
 *   "Email alerts are sent for high-priority events
 *    (e.g. account actions, new follower milestones)."
 *
 * Used internally by NotificationServiceImpl.sendEmailAlert()
 * and exposed via POST /notifications/email-alert (admin only).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAlertRequestDTO {

    @Email(message = "Must be a valid email address")
    @NotBlank(message = "toEmail is required")
    private String toEmail;

    @NotBlank(message = "subject is required")
    private String subject;

    @NotBlank(message = "body is required")
    private String body;
}