package com.connectsphere.post;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * ConnectSphere Post Service - Main Application Entry Point
 *
 * Responsibilities (as per ConnectSphere case study section 4.2):
 * - Create, edit, delete posts (text-only or with media URLs)
 * - Set post visibility: PUBLIC / FOLLOWERS_ONLY / PRIVATE
 * - Fetch personalised news feed for a user (by followee IDs list)
 * - Search posts by keyword (full-text LIKE query)
 * - Maintain denormalised counters: likesCount, commentsCount, sharesCount
 * - Soft-delete: isDeleted=true preserves the record for audit
 * - Change post visibility after creation
 * - Get post count per author
 *
 * Inter-service:
 * - JWT tokens are validated using the shared JWT secret (same as auth-service)
 * - userId is extracted from the JWT token in JwtAuthenticationFilter
 * - comment-service calls incrementComments() / decrementComments()
 * - like-service calls incrementLikes() / decrementLikes()
 * - search-service calls indexPost() after createPost()
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PostServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostServiceApplication.class, args);
        System.out.println("ConnectSphere Post Service is running...");
    }
}