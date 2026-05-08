package com.connectsphere.auth.security;

import com.connectsphere.auth.entity.AuthProvider;
import com.connectsphere.auth.entity.Role;
import com.connectsphere.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtTokenProvider — Unit Tests")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setup() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret",
                "connectsphere-super-secret-key-must-be-long-enough-for-hmac-sha256");
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiration", 604800000L);

        testUser = User.builder()
                .userId(1).username("vikash").fullName("Vikash Prajapati")
                .email("vikash@test.com").role(Role.USER).provider(AuthProvider.LOCAL).build();
    }

    @Test @DisplayName("generateAccessToken — returns non-null token string")
    void generateAccessToken_returnsToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test @DisplayName("generateAccessToken — token has 3 parts")
    void generateAccessToken_hasThreeParts() {
        assertEquals(3, jwtTokenProvider.generateAccessToken(testUser).split("\\.").length);
    }

    @Test @DisplayName("getEmailFromToken — extracts correct email")
    void getEmailFromToken_correctEmail() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        assertEquals("vikash@test.com", jwtTokenProvider.getEmailFromToken(token));
    }

    @Test @DisplayName("getRoleFromToken — extracts correct role")
    void getRoleFromToken_correctRole() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        assertEquals("USER", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test @DisplayName("getUserIdFromToken — extracts correct userId")
    void getUserIdFromToken_correctUserId() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        assertEquals(1, jwtTokenProvider.getUserIdFromToken(token));
    }

    @Test @DisplayName("extractExpiration — returns future date")
    void extractExpiration_isFuture() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        assertTrue(jwtTokenProvider.extractExpiration(token).getTime() > System.currentTimeMillis());
    }

    @Test @DisplayName("getAccessTokenExpiration — returns configured value")
    void getAccessTokenExpiration_returnsValue() {
        assertEquals(86400000L, jwtTokenProvider.getAccessTokenExpiration());
    }

    @Test @DisplayName("generateRefreshToken — returns non-null token")
    void generateRefreshToken_returnsToken() {
        String token = jwtTokenProvider.generateRefreshToken(testUser);
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test @DisplayName("generateRefreshToken — email extractable")
    void generateRefreshToken_emailExtractable() {
        String token = jwtTokenProvider.generateRefreshToken(testUser);
        assertEquals("vikash@test.com", jwtTokenProvider.getEmailFromToken(token));
    }

    @Test @DisplayName("validateToken — valid token returns true")
    void validateToken_validToken_returnsTrue() {
        assertTrue(jwtTokenProvider.validateToken(jwtTokenProvider.generateAccessToken(testUser)));
    }

    @Test @DisplayName("validateToken — malformed token returns false")
    void validateToken_malformedToken_returnsFalse() {
        assertFalse(jwtTokenProvider.validateToken("this.is.not.valid"));
    }

    @Test @DisplayName("validateToken — empty string returns false")
    void validateToken_emptyString_returnsFalse() {
        assertFalse(jwtTokenProvider.validateToken(""));
    }

    @Test @DisplayName("validateToken — null returns false")
    void validateToken_null_returnsFalse() {
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test @DisplayName("validateToken — tampered token returns false")
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        String[] parts = token.split("\\.");
        assertFalse(jwtTokenProvider.validateToken(parts[0] + ".tampered." + parts[2]));
    }

    @Test @DisplayName("generateAccessToken — ADMIN role extracted correctly")
    void generateAccessToken_adminRole() {
        testUser.setRole(Role.ADMIN);
        String token = jwtTokenProvider.generateAccessToken(testUser);
        assertEquals("ADMIN", jwtTokenProvider.getRoleFromToken(token));
    }
}