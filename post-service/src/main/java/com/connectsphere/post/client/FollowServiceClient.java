package com.connectsphere.post.client;

import com.connectsphere.post.dto.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "follow-service", path = "/api/v1")
public interface FollowServiceClient {

    @GetMapping("/follows/check/{followeeId}")
    ApiResponseDTO<Boolean> isFollowing(
            @PathVariable Integer followeeId,
            @RequestHeader("Authorization") String authHeader);

    @GetMapping("/follows/{userId}/followee-ids")
    ApiResponseDTO<List<Integer>> getFolloweeIds(
            @PathVariable("userId") Integer userId,
            @RequestHeader("Authorization") String authHeader);
}
