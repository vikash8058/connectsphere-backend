package com.connectsphere.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * ConnectSphere Auth Service - Main Application Entry Point
 *
 * Responsibilities:
 * - User Registration with OTP Email Verification
 * - Login with JWT Token generation
 * - GitHub / Google OAuth2 social login support
 * - JWT Token validation and refresh
 * - Profile management (username, bio, profilePicUrl)
 * - User search by username
 * - Account deactivation / reactivation (Admin only)
 * - Roles: GUEST (implicit), USER, ADMIN, MODERATOR
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
        System.out.println("ConnectSphere Auth Service is running...");
    }
}