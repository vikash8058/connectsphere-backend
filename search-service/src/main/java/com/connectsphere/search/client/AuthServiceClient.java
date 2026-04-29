package com.connectsphere.search.client;

import com.connectsphere.search.dto.AuthUserListApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient(
    name = "auth-service",
    path = "/api/v1"
)
public interface AuthServiceClient {

    /**
     * Search users by username or fullName.
     * Maps to: GET http://auth-service/api/v1/auth/search?query=...
     */
    @GetMapping("/auth/search")
    AuthUserListApiResponse searchUsers(@RequestParam("query") String query);
}
