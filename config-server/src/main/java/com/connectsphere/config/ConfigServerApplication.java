package com.connectsphere.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * ConnectSphere Config Server - Centralized Configuration
 *
 * Serves configuration files to all microservices.
 * Microservices fetch their config via:
 *   spring.config.import: optional:configserver:http://localhost:8888
 * (already set in auth-service application.yml)
 *
 * Config files are stored in: src/main/resources/config/
 * Format: {service-name}.yml  (e.g., auth-service.yml)
 */
@SpringBootApplication
@EnableConfigServer       // Turns this app into a Config Server
@EnableDiscoveryClient    // Registers with Eureka
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
        System.out.println("ConnectSphere Config Server is running on port 8888...");
    }
}