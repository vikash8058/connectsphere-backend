package com.connectsphere.search.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * FeignClientConfig - Propagates the incoming JWT to all outgoing Feign calls.
 *
 * Same pattern as like-service and comment-service FeignClientConfig.
 *
 * When search-service calls post-service or auth-service,
 * the outgoing Feign request has no Authorization header by default.
 * This interceptor reads the JWT from the current incoming request
 * and forwards it on every Feign call.
 *
 * For unauthenticated (guest) requests, no Authorization header is forwarded —
 * which is fine since post-service and auth-service have public GET endpoints
 * for the data search-service needs.
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
