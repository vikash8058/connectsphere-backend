package com.connectsphere.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.connectsphere.auth.repository.BlacklistedTokenRepository;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthenticationFilter - Intercepts every request
 *
 * 1. Extracts Bearer token from Authorization header
 * 2. Validates token signature and expiry
 * 3. Sets Spring Security context so @PreAuthorize works downstream
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token)) {

                // Check if the token is blacklisted (e.g., after logout)
                if (blacklistedTokenRepository.existsByToken(token)) {
                    log.warn("Blacklisted token used — rejecting request");
                    sendUnauthorizedResponse(response, "Token has been invalidated. Please login again.");
                    return; // ← STOP the filter chain, don't proceed
                }

                // Validate the token and set authentication context
                if (jwtTokenProvider.validateToken(token)) {
                    String email = jwtTokenProvider.getEmailFromToken(token);
                    String role = jwtTokenProvider.getRoleFromToken(token);

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    email, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("JWT auth set for user: {}", email);
                }
            }
        } catch (Exception e) {
            log.error("JWT filter error: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    // Helper method to extract Bearer token from Authorization header
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    // Helper method to send a consistent JSON response for unauthorized access
    private void sendUnauthorizedResponse(HttpServletResponse response,
                                          String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\": false, \"message\": \"" + message + "\"}"
        );
    }
}