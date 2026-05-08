package com.connectsphere.auth.security;

import com.connectsphere.auth.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtTokenProvider - JWT Token Generation and Validation
 *
 * Token claims (payload):
 *   sub       -> user email
 *   userId    -> integer user ID
 *   username  -> username for @mentions
 *   role      -> USER / ADMIN / MODERATOR
 *   tokenType -> ACCESS or REFRESH
 *
 * Access token:  expires in 24h (as per ConnectSphere NFR)
 * Refresh token: expires in 7 days
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshTokenExpiration;

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim("username", user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim("fullName", user.getFullName())
                .claim(CLAIM_TOKEN_TYPE, "ACCESS")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }
    
    
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_TOKEN_TYPE, "REFRESH")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);

            return true;

        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());

        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());

        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());

        } catch (IllegalArgumentException e) {
            log.warn("JWT null/empty: {}", e.getMessage());
        }

        return false;
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public String getRoleFromToken(String token) {
        return (String) Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().get(CLAIM_ROLE);
    }

    public Integer getUserIdFromToken(String token) {
        return (Integer) Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().get(CLAIM_USER_ID);
    }

    public Long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}