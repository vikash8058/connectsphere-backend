package com.connectsphere.follow.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {

            // 1. Retrieve the incoming request context from the current thread
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {

                // 2. Access the original HttpServletRequest object
                HttpServletRequest request = attributes.getRequest();

                // 3. Extract the security headers from the original request
                String userId = request.getHeader("X-User-Id");
                String role = request.getHeader("X-User-Role");
                String email = request.getHeader("X-User-Email");

                // 4. Inject them into the outgoing Feign request template
                if (userId != null)
                    requestTemplate.header("X-User-Id", userId);
                if (role != null)
                    requestTemplate.header("X-User-Role", role);
                if (email != null)
                    requestTemplate.header("X-User-Email", email);
            }
        };
    }
}
