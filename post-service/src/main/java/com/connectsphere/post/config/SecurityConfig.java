package com.connectsphere.post.config;

import com.connectsphere.post.security.JwtAuthenticationFilter;
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
 * SecurityConfig - Spring Security Configuration for Post Service
 *
 * Public endpoints (no JWT required):
 *   GET /posts/public          - Browse public posts (guests)
 *   GET /posts/{postId}        - View post detail (guests see PUBLIC only)
 *   GET /posts/user/{authorId} - View user's public timeline
 *   GET /posts/search          - Keyword search (PUBLIC posts only)
 *   GET /posts/count/{authorId}- Post count for profile badge
 *   /actuator/health           - Health check
 *   /swagger-ui/**, /api-docs/** - API docs
 *
 * Protected endpoints (valid JWT required):
 *   POST   /posts              - Create post
 *   PUT    /posts/{id}         - Update post
 *   DELETE /posts/{id}         - Delete post
 *   PATCH  /posts/{id}/visibility
 *   GET    /posts/feed         - Personalised news feed
 *
 * Internal service endpoints (JWT required, inter-service calls):
 *   POST /posts/{id}/likes/increment
 *   POST /posts/{id}/likes/decrement
 *   POST /posts/{id}/comments/increment
 *   POST /posts/{id}/comments/decrement
 *   POST /posts/{id}/shares/increment
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/posts/public",
            "/posts/search",
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
                    // Guest-accessible GET endpoints
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                    // View single post, user timeline, and count are public GET
                    .requestMatchers(HttpMethod.GET, "/posts/{postId}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/posts/user/{authorId}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/posts/count/{authorId}").permitAll()
                    // Everything else requires authentication
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