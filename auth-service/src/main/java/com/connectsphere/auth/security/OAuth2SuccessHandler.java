package com.connectsphere.auth.security;

import com.connectsphere.auth.entity.AuthProvider;
import com.connectsphere.auth.entity.Role;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OAuth2SuccessHandler - Handles successful Google / GitHub OAuth2 login.
 *
 * Flow:
 *  1. Extract email + name from OAuth2 principal attributes.
 *  2. Detect provider (GOOGLE / GITHUB) via OAuth2AuthenticationToken registrationId.
 *  3. Look up user by email:
 *     - If new user  → create account (role=USER, isEmailVerified=true, no password).
 *     - If returning → update lastLoginAt only.
 *  4. Issue a JWT access token and return it in the JSON response body.
 *
 * Note: GitHub does NOT return email in the OAuth2User attributes if the user
 * has set their email to private on GitHub. In that case email will be null.
 * A fallback placeholder is used; the user should update their email after login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        // ── 1. Detect provider ───
        // registrationId is "google" or "github" (lowercase) as registered in application.yml
        String registrationId = oauthToken.getAuthorizedClientRegistrationId(); // "google" / "github"
        AuthProvider provider;
        try {
            provider = AuthProvider.valueOf(registrationId.toUpperCase()); // GOOGLE / GITHUB
        } catch (IllegalArgumentException e) {
            log.error("Unknown OAuth2 provider: {}", registrationId);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown OAuth2 provider");
            return;
        }

        log.info("OAuth2 login via provider: {}", provider);

        // ── 2. Extract user attributes ──
        String email = oAuth2User.getAttribute("email");
        String name  = oAuth2User.getAttribute("name");

        // GitHub: email can be null if user set it private on GitHub
        if (email == null) {
            // Use login (GitHub username) as fallback identifier
            String githubLogin = oAuth2User.getAttribute("login");
            if (githubLogin != null) {
                email = githubLogin + "@github-noreply.com";
                log.warn("GitHub email not provided for login '{}', using placeholder: {}",
                        githubLogin, email);
            } else {
                log.error("No email or login found from OAuth2 provider: {}", provider);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Email not provided by OAuth2 provider. Please set your email to public.");
                return;
            }
        }

        // ── 3. Find or create user ──
        final String finalEmail = email;
        final String finalName  = (name != null && !name.isBlank()) ? name : "OAuth User";

        User user = userRepository.findByEmail(finalEmail)
                .orElseGet(() -> {
                    String username = generateUniqueUsername(finalEmail);
                    log.info("Creating new OAuth2 user: email={}, provider={}", finalEmail, provider);
                    return userRepository.save(
                            User.builder()
                                    .email(finalEmail)
                                    .fullName(finalName)
                                    .username(username)
                                    .role(Role.USER)
                                    .provider(provider)
                                    .isEmailVerified(true)   // OAuth accounts are pre-verified
                                    .isActive(true)
                                    .lastLoginAt(LocalDateTime.now())
                                    .build()
                    );
                });

        // ── 3.5 Check if user is active ──
        if (!user.getIsActive()) {
            log.warn("OAuth2 login attempt by deactivated user: {}", finalEmail);
            response.sendRedirect(frontendUrl + "/login?error=ACCOUNT_SUSPENDED");
            return;
        }

        // ── 4. Update last login ───
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // ── 5. Issue JWT and return ─────
        String accessToken  = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        log.info("OAuth2 login successful for user: {} via {}", user.getEmail(), provider);
        log.info("Redirecting to frontend: {}", frontendUrl);

        // ── 6. Redirect to frontend callback ─────
        // We pass token, userId, username, email, role as query params.
        // The frontend OAuthCallback component will parse these and save the session.
        String targetUrl = String.format("%s/oauth/callback?token=%s&userId=%d&username=%s&email=%s&role=%s&isPasswordSet=%b&profilePicUrl=%s&fullName=%s&isElite=%b",
                frontendUrl,
                accessToken,
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getPasswordHash() != null,
                user.getProfilePicUrl() != null ? user.getProfilePicUrl() : "",
                user.getFullName() != null ? user.getFullName() : "",
                user.getIsElite() != null ? user.getIsElite() : false
        );

        response.sendRedirect(targetUrl);
    }

    /**
     * Generates a unique username from the email prefix.
     * e.g. "vikash@gmail.com" → "vikash_a3f9b"
     * If the base username already exists, a random suffix is appended.
     */
    private String generateUniqueUsername(String email) {
        String base = email.split("@")[0]
                .replaceAll("[^a-zA-Z0-9._]", "_")  // sanitize special chars
                .toLowerCase();
        String candidate = base + "_" + UUID.randomUUID().toString().substring(0, 5);

        // Ensure uniqueness (very rare collision scenario)
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + UUID.randomUUID().toString().substring(0, 5);
        }
        return candidate;
    }
}