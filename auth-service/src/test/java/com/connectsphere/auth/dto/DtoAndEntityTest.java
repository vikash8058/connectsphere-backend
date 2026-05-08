package com.connectsphere.auth.dto;

import com.connectsphere.auth.entity.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;


import static org.junit.jupiter.api.Assertions.*;

class DtoAndEntityTest {

    @Test
    void testAssignRoleRequestDTO() {
        AssignRoleRequestDTO dto = new AssignRoleRequestDTO();
        dto.setRole("ADMIN");
        assertEquals("ADMIN", dto.getRole());

        AssignRoleRequestDTO dto2 = AssignRoleRequestDTO.builder().role("USER").build();
        assertEquals("USER", dto2.getRole());

        AssignRoleRequestDTO dto3 = new AssignRoleRequestDTO("MODERATOR");
        assertEquals("MODERATOR", dto3.getRole());
    }

    @Test
    void testSetPasswordRequestDTO() {
        SetPasswordRequestDTO dto = new SetPasswordRequestDTO();
        dto.setNewPassword("newPass123");
        dto.setConfirmPassword("newPass123");
        assertEquals("newPass123", dto.getNewPassword());
        assertEquals("newPass123", dto.getConfirmPassword());

        SetPasswordRequestDTO dto2 = new SetPasswordRequestDTO("p1", "p1");
        assertEquals("p1", dto2.getNewPassword());
    }

    @Test
    void testLoginResponseDTO() {
        LoginResponseDTO dto = LoginResponseDTO.builder()
                .accessToken("token")
                .refreshToken("refresh")
                .userId(1)                // Changed 1L to 1 (Integer)
                .username("user")
                .email("test@test.com")
                .role(Role.USER)          // Changed String "USER" to Role.USER enum
                .build();

        assertEquals("token", dto.getAccessToken());
        assertEquals("refresh", dto.getRefreshToken());
        assertEquals(1, dto.getUserId()); // Changed 1L to 1
        assertEquals("user", dto.getUsername());
        assertEquals("test@test.com", dto.getEmail());
        assertEquals(Role.USER, dto.getRole()); // Changed String check to Enum check

        LoginResponseDTO dto2 = new LoginResponseDTO();
        dto2.setAccessToken("t");
        assertEquals("t", dto2.getAccessToken());
    }


    @Test
    void testBlacklistedToken() {
        LocalDateTime now = LocalDateTime.now();
        BlacklistedToken token = new BlacklistedToken();
        token.setId(1L);
        token.setToken("token");
        token.setExpiryDate(now);

        assertEquals(1L, token.getId());
        assertEquals("token", token.getToken());
        assertEquals(now, token.getExpiryDate());

        BlacklistedToken token2 = new BlacklistedToken(2L, "t2", now);
        assertEquals(2L, token2.getId());
    }

    @Test
    void testOtpVerification() {
        LocalDateTime now = LocalDateTime.now();
        OtpVerification otp = OtpVerification.builder()
                .otpId(1)
                .email("a@b.com")
                .otpCode("123456")
                .otpType(OtpType.EMAIL_VERIFICATION)
                .expiresAt(now)
                .isUsed(false)
                .build();

        assertEquals(1, otp.getOtpId());
        assertEquals("a@b.com", otp.getEmail());
        assertEquals("123456", otp.getOtpCode());
        assertEquals(OtpType.EMAIL_VERIFICATION, otp.getOtpType());
        assertEquals(now, otp.getExpiresAt());
        assertFalse(otp.getIsUsed());

        otp.setIsUsed(true);
        assertTrue(otp.getIsUsed());
        
        OtpVerification otp2 = new OtpVerification();
        otp2.setOtpId(2);
        assertEquals(2, otp2.getOtpId());
    }

    @Test
    void testLoginResponseDTO_Full() {
        LocalDateTime now = LocalDateTime.now();
        LoginResponseDTO dto = LoginResponseDTO.builder()
                .accessToken("at").refreshToken("rt").tokenType("Bearer")
                .expiresIn(3600L).userId(1).username("u").fullName("fn")
                .email("e").bio("b").profilePicUrl("p").role(Role.USER)
                .isPasswordSet(true).isElite(true).eliteUntil(now)
                .build();

        assertEquals("at", dto.getAccessToken());
        assertEquals("rt", dto.getRefreshToken());
        assertEquals("Bearer", dto.getTokenType());
        assertEquals(3600L, dto.getExpiresIn());
        assertEquals(1, dto.getUserId());
        assertEquals("u", dto.getUsername());
        assertEquals("fn", dto.getFullName());
        assertEquals("e", dto.getEmail());
        assertEquals("b", dto.getBio());
        assertEquals("p", dto.getProfilePicUrl());
        assertEquals(Role.USER, dto.getRole());
        assertTrue(dto.isPasswordSet());
        assertTrue(dto.getIsElite());
        assertEquals(now, dto.getEliteUntil());
    }

    @Test
    void testLombokMethods() {
        // Test BlacklistedToken equals/hashCode/toString
        LocalDateTime now = LocalDateTime.now();
        BlacklistedToken t1 = new BlacklistedToken(1L, "token", now);
        BlacklistedToken t2 = new BlacklistedToken(1L, "token", now);
        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertNotNull(t1.toString());

        // Test SetPasswordRequestDTO equals/hashCode/toString
        SetPasswordRequestDTO d1 = new SetPasswordRequestDTO("p", "p");
        SetPasswordRequestDTO d2 = new SetPasswordRequestDTO("p", "p");
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
        assertNotNull(d1.toString());
    }

}
