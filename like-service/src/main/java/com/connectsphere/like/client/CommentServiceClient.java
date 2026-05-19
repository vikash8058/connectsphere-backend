package com.connectsphere.like.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
    name = "comment-service",
    path = "/api/v1"
)
public interface CommentServiceClient {
    @PostMapping("/comments/{commentId}/like")
    void incrementLikeCount(@PathVariable("commentId") Integer commentId);
    
    @PostMapping("/comments/{commentId}/unlike")
    void decrementLikeCount(@PathVariable("commentId") Integer commentId);
}