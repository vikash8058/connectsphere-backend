package com.connectsphere.follow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * ConnectSphere Follow Service - Main Application Entry Point
 *
 * Responsibilities (case study section 4.5):
 * - Manages directed social graph between users (follower → followee)
 * - follow() / unfollow() with duplicate prevention via DB unique constraint
 * - isFollowing() check (used by post-service for FOLLOWERS_ONLY visibility)
 * - getFollowers() / getFollowing() — social graph traversal
 * - getFollowerCount() / getFollowingCount() — profile badge counts
 * - getMutualFollows() — mutual connections for moderation trust scoring
 * - getSuggestedUsers() — "People You May Know" based on second-degree connections
 *
 * Case study section 2.3:
 * "Follow and unfollow other users; view a personalized news feed based on followees."
 * "View follower and following lists; see mutual connections."
 * "Discover suggested users to follow based on mutual connections."
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class FollowServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FollowServiceApplication.class, args);
        System.out.println("ConnectSphere Follow Service is running on port 8085...");
    }
}