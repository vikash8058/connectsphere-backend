package com.connectsphere.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;


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