package com.connectsphere.comment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * ConnectSphere Comment Service - Main Application Entry Point
 *
 * Responsibilities (as per ConnectSphere case study section 4.3):
 * - Add top-level comments to posts (parentCommentId = null)
 * - Add nested replies to existing comments (parentCommentId = parent's ID)
 * - Edit own comments (only content, updates isEdited flag)
 * - Soft-delete comments (isDeleted = true, preserves thread structure)
 * - Like / Unlike comments (likesCount counter)
 * - Get all top-level comments for a post
 * - Get all replies for a specific comment
 * - Get all comments by a specific user
 * - Get comment count for a post
 *
 * Inter-service:
 * - After addComment()    → calls POST-SERVICE: /posts/{postId}/comments/increment
 * - After deleteComment() → calls POST-SERVICE: /posts/{postId}/comments/decrement
 * - JWT tokens validated using the shared JWT secret (same as auth-service)
 * - userId extracted from JWT in JwtAuthenticationFilter
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients  // Enables Feign clients for inter-service communication (e.g., PostServiceClient)
public class CommentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentServiceApplication.class, args);
        System.out.println("ConnectSphere Comment Service is running...");
    }
}