package com.connectsphere.search.client;

import com.connectsphere.search.dto.PostApiResponse;
import com.connectsphere.search.dto.PostListApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PostServiceClient - Feign Client for inter-service calls to post-service
 *
 * name = "post-service" → resolved via Eureka (lb://post-service)
 * path = "/api/v1"      → matches post-service context-path
 *
 * Endpoints used by search-service:
 *   GET /posts/{postId}       → enrich a single postId with full post data
 *   GET /posts/search?keyword → full-text keyword search (delegated to post-service)
 */
@FeignClient(
    name = "post-service",
    path = "/api/v1"
)
public interface PostServiceClient {

    /**
     * Get a single post by ID — used to enrich postIds from local index.
     */
    @GetMapping("/posts/{postId}")
    PostApiResponse getPostById(@PathVariable("postId") Integer postId);

    /**
     * Full-text keyword search across post content.
     * post-service performs the DB query; search-service attaches hashtags.
     */
    @GetMapping("/posts/search")
    PostListApiResponse searchPosts(@RequestParam("keyword") String keyword);
}
