package com.connectsphere.comment.config;

import com.connectsphere.comment.security.JwtAuthenticationFilter;
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
 * SecurityConfig - Spring Security for Comment Service
 *
 * Public endpoints (no JWT):
 *   GET /comments/post/{postId}          - View comments on a post
 *   GET /comments/post/{postId}/top-level
 *   GET /comments/{commentId}
 *   GET /comments/{commentId}/replies
 *   GET /comments/count/{postId}
 *   /actuator/health, /swagger-ui/**, /api-docs/**
 *
 * Protected (JWT required):
 *   POST   /comments                     - Add comment
 *   PUT    /comments/{commentId}         - Update comment
 *   DELETE /comments/{commentId}         - Delete comment
 *   POST   /comments/{commentId}/like
 *   POST   /comments/{commentId}/unlike
 *   GET    /comments/user/{authorId}     - User's comment history
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
                    // Public GET endpoints
                    .requestMatchers(HttpMethod.GET, "/comments/post/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/comments/{commentId}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/comments/{commentId}/replies").permitAll()
                    .requestMatchers(HttpMethod.GET, "/comments/user/{id}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/comments/count/**").permitAll()
                    .requestMatchers(HttpMethod.GET,
                            "/actuator/health",
                            "/swagger-ui.html",
                            "/swagger-ui/**",
                            "/api-docs/**",
                            "/v3/api-docs/**").permitAll()
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