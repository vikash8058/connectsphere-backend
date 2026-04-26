package com.connectsphere.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * ApplicationConfig - General application configuration for Media Service
 *
 * Registers:
 *   RestTemplate - Used for inter-service HTTP calls (e.g. future integration
 *                  with post-service to notify about media soft-delete, or with
 *                  notification-service to send upload alerts).
 */
@Configuration
public class ApplicationConfig {

    /**
     * RestTemplate bean for synchronous inter-service REST calls.
     * e.g. Media Service → Post Service (soft-delete notification)
     *      Media Service → Notification Service (story creation alert)
     *
     * In production consider RestTemplate with circuit breaker (Resilience4j)
     * or replace with WebClient for reactive inter-service calls.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Global CORS configuration for development.
     * Allows frontend (Port 5173) to access media assets directly from 8087 or 8080.
     */
    @Bean
    public org.springframework.web.servlet.config.annotation.WebMvcConfigurer corsConfigurer() {
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@org.springframework.lang.NonNull org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
