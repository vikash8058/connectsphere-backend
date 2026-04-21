package com.connectsphere.media.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JwtAuthenticationFilter - JWT Token Validation Filter for Media Service
 *
 * Mirrors the exact same implementation as post-service JwtAuthenticationFilter.
 * Validates the JWT token issued by auth-service using the shared secret.
 *
 * Flow:
 *   1. Extract "Authorization: Bearer <token>" header
 *   2. Parse and validate JWT using the shared secret (jwt.secret)
 *   3. Extract userId (subject) and role (claim) from the token
 *   4. Set request attributes: requestingUserId (Integer), requestingUserRole (String)
 *   5. Set Spring SecurityContext with the authenticated principal
 *
 * These attributes are read by MediaResource to identify the calling user.
 * userId is NEVER read from the request body — always from the validated JWT.
 *
 * Public endpoints (listed in SecurityConfig) bypass this filter's authentication check,
 * but the filter still runs — it just does not block unauthenticated requests for them.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No Authorization header present — pass through (SecurityConfig handles 401)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parseToken(token);

            // Extract userId from JWT subject
            Object userIdClaim = claims.get("userId");
            Integer userId = userIdClaim != null
                    ? ((Number) userIdClaim).intValue()
                    : null;

            // Extract role from JWT claims (set by auth-service on login/register)
            String role = claims.get("role", String.class);
            if (role == null) role = "USER";

            // Set request attributes — read by MediaResource
            request.setAttribute("requestingUserId", userId);
            request.setAttribute("requestingUserRole", role);

            // Set Spring Security context (needed for @PreAuthorize if used)
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT validated: userId={}, role={}", userId, role);

        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", ex.getMessage());
            sendUnauthorizedError(response, "Token has expired. Please log in again.");
            return;
        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException ex) {
            log.warn("Invalid JWT: {}", ex.getMessage());
            sendUnauthorizedError(response, "Invalid token. Authentication failed.");
            return;
        } catch (Exception ex) {
            log.error("JWT processing error: {}", ex.getMessage());
            sendUnauthorizedError(response, "Authentication error. Please try again.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Parse and validate the JWT token using the shared secret from auth-service.
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Write a 401 JSON error response directly to the response stream.
     * Used when JWT validation fails for a protected endpoint.
     */
    private void sendUnauthorizedError(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\"}"
        );
    }
}
