package com.connectsphere.like.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * CommentServiceClient - Feign Client for inter-service calls to comment-service
 *
 * name = "comment-service"
 *   → Feign resolves via Eureka (lb://comment-service)
 *
 * path = "/api/v1"
 *   → Matches comment-service server.servlet.context-path
 *
 * Endpoints called after like/unlike on a COMMENT target:
 *   POST /comments/{commentId}/likes/increment  (inline counter on Comment entity)
 *   POST /comments/{commentId}/likes/decrement
 */
@FeignClient(
    name = "comment-service",
    path = "/api/v1"
)
public interface CommentServiceClient {

    /**
     * Increment likesCount on a comment after a successful like.
     * Maps to: POST http://comment-service/api/v1/comments/{commentId}/like
     */
    @PostMapping("/comments/{commentId}/like")
    void incrementLikeCount(@PathVariable("commentId") Integer commentId);

    /**
     * Decrement likesCount on a comment after a successful unlike.
     * Maps to: POST http://comment-service/api/v1/comments/{commentId}/unlike
     */
    @PostMapping("/comments/{commentId}/unlike")
    void decrementLikeCount(@PathVariable("commentId") Integer commentId);
}