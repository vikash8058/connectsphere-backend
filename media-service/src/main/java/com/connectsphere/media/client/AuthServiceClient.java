package com.connectsphere.media.client;

import com.connectsphere.media.dto.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/api/v1/auth/users/{userId}")
    ApiResponseDTO<Map<String, Object>> getUserById(@PathVariable("userId") Integer userId);
}
