package com.connectsphere.auth.dto;

import com.connectsphere.auth.entity.OtpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * OtpVerifyRequestDTO - Payload for OTP verification
 *
 * Used for both EMAIL_VERIFICATION and PASSWORD_RESET flows.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerifyRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "OTP code is required")
    private String otpCode;

    @NotNull(message = "OTP type is required")
    private OtpType otpType;
}