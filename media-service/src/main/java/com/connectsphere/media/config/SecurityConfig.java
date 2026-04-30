package com.connectsphere.media.config;

import com.connectsphere.media.security.GatewayHeaderFilter;
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
 * Centralized security now handled by API Gateway.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final GatewayHeaderFilter gatewayHeaderFilter;

	private static final String[] PUBLIC_GET_ENDPOINTS = { "/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
			"/api-docs/**", "/v3/api-docs/**" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
				.requestMatchers(HttpMethod.GET, "/media/{mediaId}").permitAll()
				.requestMatchers(HttpMethod.GET, "/media/post/{postId}").permitAll()
				.requestMatchers(HttpMethod.GET, "/media/uploader/{uploaderId}").permitAll()
				.requestMatchers(HttpMethod.GET, "/media/cdn/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/media/cdn/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/stories/user/{authorId}").permitAll()
				.anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(gatewayHeaderFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
