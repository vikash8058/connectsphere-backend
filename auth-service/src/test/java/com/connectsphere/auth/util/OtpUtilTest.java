package com.connectsphere.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OtpUtil — Unit Tests")
class OtpUtilTest {

    @Test @DisplayName("generateOtp — returns 6 digit string")
    void generateOtp_sixDigits() {
        assertEquals(6, OtpUtil.generateOtp().length());
    }

    @Test @DisplayName("generateOtp — contains only digits")
    void generateOtp_onlyDigits() {
        assertTrue(OtpUtil.generateOtp().matches("\\d{6}"));
    }

    @Test @DisplayName("generateOtp — value between 100000 and 999999")
    void generateOtp_inRange() {
        int value = Integer.parseInt(OtpUtil.generateOtp());
        assertTrue(value >= 100000 && value <= 999999);
    }

    @RepeatedTest(10) @DisplayName("generateOtp — always 6 digits")
    void generateOtp_alwaysSixDigits() {
        assertEquals(6, OtpUtil.generateOtp().length());
    }
}