package com.connectsphere.media.client;

import com.connectsphere.media.dto.ApiResponseDTO;
import com.connectsphere.media.dto.PostSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "post-service")
public interface PostServiceClient {

    @GetMapping("/api/v1/posts/{postId}")
    ApiResponseDTO<PostSummaryDTO> getPostById(@PathVariable Integer postId);
}