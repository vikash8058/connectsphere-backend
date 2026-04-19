package com.connectsphere.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ApplicationConfig - General application configuration
 *
 * @EnableAsync enables @Async on EmailService so OTP emails
 * are sent in background threads without blocking the API response.
 */
@Configuration
@EnableAsync
public class ApplicationConfig {
}