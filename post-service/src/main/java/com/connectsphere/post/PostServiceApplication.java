package com.connectsphere.post;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * ConnectSphere Post Service - Main Application Entry Point
...
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class PostServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostServiceApplication.class, args);
        System.out.println("ConnectSphere Post Service is running...");
    }
}