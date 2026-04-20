package com.connectsphere.like;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * ConnectSphere Like Service - Main Application Entry Point
 *
 * @EnableFeignClients — activates all @FeignClient interfaces in this package.
 * Feign replaces RestTemplate for inter-service calls to post-service
 * and comment-service (counter sync after like/unlike).
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients   // Scans for all @FeignClient interfaces in this package
public class LikeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LikeServiceApplication.class, args);
        System.out.println("ConnectSphere Like Service is running on port 8084...");
    }
}