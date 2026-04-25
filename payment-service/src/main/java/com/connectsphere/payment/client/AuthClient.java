package com.connectsphere.payment.client;

import com.connectsphere.payment.dto.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service")
public interface AuthClient {

    @PutMapping("/api/v1/auth/internal/update-elite-status")
    ApiResponseDTO<String> updateEliteStatus(
            @RequestParam("userId") Integer userId,
            @RequestParam("isElite") Boolean isElite,
            @RequestParam("eliteUntil") String eliteUntil);
}
