package com.connectsphere.like.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * ApplicationConfig - General application configuration for Like Service
 *
 * RestTemplate bean for inter-service REST calls:
 *   → post-service:    increment/decrement post likesCount
 *   → comment-service: increment/decrement comment likesCount
 */
@Configuration
public class ApplicationConfig {

}