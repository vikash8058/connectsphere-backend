package com.connectsphere.follow.config;

import org.springframework.context.annotation.Configuration;

/**
 * ApplicationConfig - General application configuration for Follow Service
 *
 * No RestTemplate needed — follow-service does not call other services.
 * (Notifications for follow events will be handled by notification-service
 *  in a later UC via an event-driven pattern.)
 */
@Configuration
public class ApplicationConfig {
}