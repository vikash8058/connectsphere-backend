package com.connectsphere.follow.client;

import com.connectsphere.follow.dto.UserExistsResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "auth-service",
        path = "/api/v1"
)
public interface AuthServiceClient {

    // Reuses the existing GET /auth/users/{userId} endpoint
    @GetMapping("/auth/users/{userId}")
    UserExistsResponseDTO getUserById(
            @PathVariable("userId") Integer userId,
            @RequestHeader("Authorization") String authorizationHeader
    );
}