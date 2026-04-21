package com.connectsphere.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ConnectSphere Media Service - Main Application Entry Point
 *
 * Responsibilities (as per ConnectSphere case study section 4.7):
 * - Upload images (JPEG, PNG, WebP) and short videos (MP4) up to configurable size limits
 * - Store uploaded media on a CDN-backed object store; URL linked to post or story
 * - Create and manage Stories (ephemeral media posts) visible for 24 hours
 * - Track story view counts (atomically incremented when another user opens a story)
 * - Soft-delete media attached to deleted posts (preserved for audit trail)
 * - Expire stories exactly 24 hours after creation via @Scheduled cleanup job (NFR: within 5 min)
 *
 * Inter-service interactions:
 * - JWT tokens validated using the shared secret from auth-service
 * - post-service sends mediaUrls (CDN URLs) in CreatePostRequest after upload
 * - Soft-deleted media records are retained for 30 days (NFR: audit trail)
 *
 * Scheduler: @EnableScheduling activates the StoryExpiryScheduler
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableFeignClients
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
        System.out.println("ConnectSphere Media Service is running...");
    }
}
