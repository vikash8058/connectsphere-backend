package com.connectsphere.comment.client;

import com.connectsphere.comment.dto.PostApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "post-service",
        url  = "${post-service.base-url}"
)
public interface PostClient {

    @GetMapping("/posts/{postId}")
    PostApiResponse getPostById(@PathVariable("postId") Integer postId);

    @PostMapping("/posts/{postId}/comments/increment")
    void incrementCommentCount(@PathVariable("postId") Integer postId);

    @PostMapping("/posts/{postId}/comments/decrement")
    void decrementCommentCount(@PathVariable("postId") Integer postId);
}