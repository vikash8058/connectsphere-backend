package com.connectsphere.like.config;

import com.connectsphere.like.security.JwtAuthenticationFilter;
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
 * SecurityConfig - Spring Security Configuration for Like Service
 *
 * Public (no JWT):
 *   GET  /likes/target          - View reactions on a post/comment
 *   GET  /likes/count           - Total reaction count
 *   GET  /likes/count/type      - Count by type
 *   GET  /likes/summary         - Emoji reaction bar
 *   GET  /likes/user/{userId}   - Reactions by a user
 *   /actuator/health, /swagger-ui/**, /api-docs/**
 *
 * Protected (JWT required):
 *   POST   /likes               - React
 *   DELETE /likes               - Unlike
 *   PUT    /likes/change        - Change reaction
 *   GET    /likes/has           - hasLiked (needs userId from JWT)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/likes/target",
            "/likes/count",
            "/likes/count/type",
            "/likes/summary",
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
                    // User reaction history — public read
                    .requestMatchers(HttpMethod.GET, "/likes/user/{userId}").permitAll()
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