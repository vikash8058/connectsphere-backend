package com.connectsphere.like.client;

import com.connectsphere.like.dto.PostApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;


@FeignClient(
    name = "post-service",
    path = "/api/v1"
)
public interface PostServiceClient {

    @PostMapping("/posts/{postId}/likes/increment")
    void incrementLikeCount(@PathVariable("postId") Integer postId);

    @PostMapping("/posts/{postId}/likes/decrement")
    void decrementLikeCount(@PathVariable("postId") Integer postId);

    @GetMapping("/posts/{postId}")
    PostApiResponse getPostById(@PathVariable("postId") Integer postId);
}