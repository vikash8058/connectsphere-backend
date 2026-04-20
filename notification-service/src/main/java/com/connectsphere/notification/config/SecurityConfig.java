package com.connectsphere.notification.config;

import com.connectsphere.notification.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig - Spring Security for Notification Service
 *
 * Public endpoints (no JWT):
 *   POST /notifications/internal   - Service-to-service notification creation
 *   /actuator/health, /swagger-ui/**, /api-docs/**
 *
 * Protected (JWT required):
 *   All other /notifications/** endpoints
 *
 * Admin-only endpoints further protected via @PreAuthorize("hasRole('ADMIN')")
 * in NotificationResource:
 *   POST /notifications/bulk
 *   POST /notifications/email-alert
 *   GET  /notifications/all
 *   GET  /notifications/type/{type}
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    // Internal service-to-service endpoint — open (called by like/comment/follow services)
                    .requestMatchers(HttpMethod.POST, "/notifications/internal").permitAll()
                    // Actuator & Swagger — public
                    .requestMatchers(
                            "/actuator/health",
                            "/swagger-ui.html",
                            "/swagger-ui/**",
                            "/api-docs/**",
                            "/v3/api-docs/**").permitAll()
                    // Everything else requires a valid JWT
                    .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}