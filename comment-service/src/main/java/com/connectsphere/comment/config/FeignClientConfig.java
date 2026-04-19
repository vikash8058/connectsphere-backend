package com.connectsphere.comment.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * FeignClientConfig - Forwards JWT token from incoming request to outgoing Feign calls.
 * Without this, Feign calls to post-service have no Authorization header → 403.
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor jwtForwardingInterceptor() {
        return requestTemplate -> {
            // Get the current incoming HTTP request
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");

                // Forward the same JWT to post-service
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    requestTemplate.header("Authorization", authHeader);
                }
            }
        };
    }
}