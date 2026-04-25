package com.connectsphere.media.client;

import com.connectsphere.media.dto.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "follow-service", path = "/api/v1/follows")
public interface FollowServiceClient {

    @GetMapping("/{userId}/followee-ids")
    ApiResponseDTO<List<Integer>> getFolloweeIds(@PathVariable("userId") Integer userId);

    @GetMapping("/{userId}/following")
    ApiResponseDTO<List<java.util.Map<String, Object>>> getFollowing(@PathVariable("userId") Integer userId);

    @GetMapping("/check/{followeeId}")
    ApiResponseDTO<Boolean> isFollowing(
            @PathVariable("followeeId") Integer followeeId,
            @RequestHeader("Authorization") String authHeader);
}
