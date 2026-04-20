package com.connectsphere.auth.config;

import com.connectsphere.auth.security.JwtAuthenticationFilter;
import com.connectsphere.auth.security.OAuth2SuccessHandler;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig - Spring Security Configuration for ConnectSphere Auth Service
 *
 * Public endpoints: register, login, OTP flows, OAuth callback, token refresh,
 *                   forgot/reset password, Swagger, Actuator health
 * Admin-only: user management endpoints
 * All others: require valid JWT
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/register",
            "/auth/login",
            "/auth/verify-otp",
            "/auth/resend-otp",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/refresh",
            "/auth/oauth/**",
            "/actuator/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/api-docs/**",
            "/v3/api-docs/**",
            "/oauth2/**",
            "/login/**",
            "/auth/users/**",
            "/auth/search",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                    .requestMatchers("/auth/admin/**").hasRole("ADMIN")
                    .requestMatchers("/auth/moderator/**").hasAnyRole("ADMIN", "MODERATOR")
                    .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .oauth2Login(oauth -> oauth
                    .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}