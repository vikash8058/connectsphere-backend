package com.connectsphere.comment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * ApplicationConfig - General application configuration for Comment Service
 *
 * Registers RestTemplate as a Spring Bean.
 * Used by CommentServiceImpl to make inter-service REST calls to post-service.
 */
@Configuration
public class ApplicationConfig {

    /**
     * RestTemplate bean for synchronous inter-service HTTP calls.
     * Used to call post-service counter endpoints after comment operations.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}