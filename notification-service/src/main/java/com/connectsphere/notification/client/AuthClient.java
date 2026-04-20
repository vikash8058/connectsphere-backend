package com.connectsphere.notification.client;

import com.connectsphere.notification.dto.UserDataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * AuthClient - Feign client to fetch user details from auth-service
 *
 * Used by NotificationServiceImpl to:
 *   1. Get actor's username → build real message like "vikash liked your post"
 *   2. Get recipient's email → send real email alert
 */
@FeignClient(name = "auth-service", path = "/api/v1")
public interface AuthClient {

    @GetMapping("/auth/users/{userId}")
    UserDataDTO getUserById(@PathVariable Integer userId);
}