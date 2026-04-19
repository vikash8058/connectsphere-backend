package com.connectsphere.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * ConnectSphere API Gateway - Single Entry Point
 *
 * Routes all client requests to the correct microservice.
 * Validates JWT tokens before forwarding protected requests.
 * All microservices remain hidden behind this gateway.
 *
 * Port: 8080
 * Route pattern: /api/v1/{service}/** → lb://{service-name}
 */
@SpringBootApplication
@EnableDiscoveryClient   // Registers with Eureka and discovers services
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
        System.out.println("ConnectSphere API Gateway is running on port 8080...");
    }
}