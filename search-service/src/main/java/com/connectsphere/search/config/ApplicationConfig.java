package com.connectsphere.search.config;

import org.springframework.context.annotation.Configuration;

/**
 * ApplicationConfig - General application configuration for Search Service
 *
 * Feign clients are configured via FeignClientConfig.
 * RabbitMQ beans are in RabbitMQConfig.
 * No RestTemplate needed — all inter-service calls use OpenFeign.
 */
@Configuration
public class ApplicationConfig {
}
