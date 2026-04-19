package com.connectsphere.auth.util;

import java.security.SecureRandom;

/**
 * OtpUtil - Cryptographically secure 6-digit OTP generator
 */
public class OtpUtil {

    private static final SecureRandom secureRandom = new SecureRandom();

    private OtpUtil() {}

    public static String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }
}