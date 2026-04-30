package com.connectsphere.notification.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {  //Feign interface for intercepting HTTP requests
        return requestTemplate -> {

            // Step 1: Get the current HTTP request
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attributes != null) {

                // Step 2: Extract headers from incoming request
                HttpServletRequest request = attributes.getRequest();
                String userId = request.getHeader("X-User-Id");
                String role = request.getHeader("X-User-Role");
                String email = request.getHeader("X-User-Email");

                // Step 3: Add them to the outgoing Feign request
                if (userId != null) requestTemplate.header("X-User-Id", userId);
                if (role != null) requestTemplate.header("X-User-Role", role);
                if (email != null) requestTemplate.header("X-User-Email", email);
            }
        };
    }
}
