package com.connectsphere.like.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * FeignClientConfig - Propagates the incoming JWT token to all outgoing Feign calls.
 *
 * Problem this solves:
 *   When like-service calls post-service via Feign (to increment likesCount),
 *   the outgoing request has NO Authorization header by default.
 *   Post-service rejects it with 403 Forbidden.
 *
 * Solution:
 *   This interceptor reads the JWT from the current incoming request
 *   and forwards it as the Authorization header on every Feign call.
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor jwtFeignInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    requestTemplate.header("Authorization", authHeader);
                }
            }
        };
    }
}