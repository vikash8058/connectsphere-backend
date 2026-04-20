package com.connectsphere.follow.config;

import com.connectsphere.follow.security.JwtAuthenticationFilter;
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
 * SecurityConfig - Spring Security Configuration for Follow Service
 *
 * Public (no JWT):
 *   GET /follows/{userId}/followers
 *   GET /follows/{userId}/following
 *   GET /follows/{userId}/follower-count
 *   GET /follows/{userId}/following-count
 *   GET /follows/{userId}/counts
 *   GET /follows/{userId}/mutual
 *   GET /follows/{userId}/followee-ids    (called by post-service for feed)
 *   /actuator/health, /swagger-ui/**, /api-docs/**
 *
 * Protected (JWT required):
 *   POST   /follows/{followeeId}          - Follow
 *   DELETE /follows/{followeeId}          - Unfollow
 *   GET    /follows/check/{followeeId}    - isFollowing (needs current userId)
 *   GET    /follows/suggestions           - Suggestions (needs current userId)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/api-docs/**",
            "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                    // Public social graph reads
                    .requestMatchers(HttpMethod.GET, "/follows/*/followers").permitAll()
                    .requestMatchers(HttpMethod.GET, "/follows/*/following").permitAll()
                    .requestMatchers(HttpMethod.GET, "/follows/*/follower-count").permitAll()
                    .requestMatchers(HttpMethod.GET, "/follows/*/following-count").permitAll()
                    .requestMatchers(HttpMethod.GET, "/follows/*/counts").permitAll()
                    .requestMatchers(HttpMethod.GET, "/follows/*/mutual").permitAll()
                    .requestMatchers(HttpMethod.GET, "/follows/*/followee-ids").permitAll()
                    // Everything else requires JWT
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