package com.connectsphere.like.client;

import com.connectsphere.like.dto.PostApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * PostServiceClient - Feign Client for inter-service calls to post-service
 *
 * name = "post-service"
 *   → Feign resolves via Eureka (lb://post-service)
 *   → No hardcoded host/port
 *
 * path = "/api/v1"
 *   → Matches post-service server.servlet.context-path
 *
 * Endpoints called after like/unlike on a POST target:
 *   POST /posts/{postId}/likes/increment
 *   POST /posts/{postId}/likes/decrement
 */
@FeignClient(
    name = "post-service",
    path = "/api/v1"
)
public interface PostServiceClient {

    /**
     * Increment likesCount on a post after a successful like.
     * Maps to: POST http://post-service/api/v1/posts/{postId}/likes/increment
     */
    @PostMapping("/posts/{postId}/likes/increment")
    void incrementLikeCount(@PathVariable("postId") Integer postId);

    /**
     * Decrement likesCount on a post after a successful unlike.
     * Maps to: POST http://post-service/api/v1/posts/{postId}/likes/decrement
     */
    @PostMapping("/posts/{postId}/likes/decrement")
    void decrementLikeCount(@PathVariable("postId") Integer postId);

    @GetMapping("/posts/{postId}")
    PostApiResponse getPostById(@PathVariable("postId") Integer postId);
}