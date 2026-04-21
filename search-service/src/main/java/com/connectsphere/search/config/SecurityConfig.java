package com.connectsphere.search.config;

import com.connectsphere.search.security.JwtAuthenticationFilter;
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
 * SecurityConfig - Spring Security Configuration for Search Service
 *
 * All search and hashtag endpoints are PUBLIC (no JWT required).
 * Case study section 2.2: Guests can search users/posts and view trending hashtags.
 *
 * Public GET endpoints:
 *   /search/posts, /search/users
 *   /hashtags/trending, /hashtags/{tag}/posts
 *   /hashtags/post/{postId}, /hashtags/search, /hashtags/{tag}/count
 *   /actuator/health, /swagger-ui/**, /api-docs/**
 *
 * No protected endpoints in search-service — it only reads data.
 * JWT filter is still applied so future admin-only endpoints can be added.
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
                     // All search and hashtag endpoints are public
                     .requestMatchers(HttpMethod.GET,
                             "/search/posts",
                             "/search/users",
                             "/hashtags/trending",
                             "/hashtags/search",
                             "/hashtags/post/**",
                             "/hashtags/*/posts",
                             "/hashtags/*/count",
                             "/actuator/health",
                             "/swagger-ui.html",
                             "/swagger-ui/**",
                             "/api-docs/**",
                             "/v3/api-docs/**"
                     ).permitAll()
                     // Everything else (future admin endpoints) requires JWT
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
