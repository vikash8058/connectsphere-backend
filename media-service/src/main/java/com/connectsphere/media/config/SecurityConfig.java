package com.connectsphere.media.config;

import com.connectsphere.media.security.JwtAuthenticationFilter;
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
 * SecurityConfig - Spring Security Configuration for Media Service
 *
 * Public endpoints (no JWT required):
 *   GET /media/{mediaId}               - View media item metadata
 *   GET /media/post/{postId}           - View all media for a post
 *   GET /media/uploader/{uploaderId}   - View all media by a user
 *   GET /stories/user/{authorId}       - View active stories by a user (public profiles)
 *   /actuator/health                   - Health check
 *   /swagger-ui/**, /api-docs/**       - API documentation
 *
 * Protected endpoints (valid JWT required — all others):
 *   POST   /media/upload               - Upload media file
 *   DELETE /media/{mediaId}            - Delete media
 *   PATCH  /media/{mediaId}/link/**    - Link media to post
 *   DELETE /media/post/**              - Soft-delete by post (inter-service)
 *   POST   /stories                    - Create story
 *   GET    /stories/feed               - Get stories feed
 *   GET    /stories/{storyId}/view     - View story
 *   DELETE /stories/{storyId}          - Delete story
 */

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	private static final String[] PUBLIC_GET_ENDPOINTS = { "/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
			"/api-docs/**", "/v3/api-docs/**" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(auth -> auth
				// Actuator & docs — always public
				.requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()

				// Public media read endpoints (guests can view media metadata)
				.requestMatchers(HttpMethod.GET, "/media/{mediaId}").permitAll()
				.requestMatchers(HttpMethod.GET, "/media/post/{postId}").permitAll()
				.requestMatchers(HttpMethod.GET, "/media/uploader/{uploaderId}").permitAll()

				// Public story read endpoint (guests can see user's active stories)
				.requestMatchers(HttpMethod.GET, "/stories/user/{authorId}").permitAll()

				// Everything else requires a valid JWT
				.anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
