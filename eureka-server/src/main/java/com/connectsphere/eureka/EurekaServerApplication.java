package com.connectsphere.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * ConnectSphere Eureka Server - Service Registry
 *
 * All microservices (auth, post, comment, like, follow,
 * notification, media, search) register here at startup.
 * API Gateway uses this to discover service URLs dynamically.
 *
 * Dashboard: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer   // Turns this app into a Eureka registry
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
        System.out.println("ConnectSphere Eureka Server is running on port 8761...");
    }
}