package com.connectsphere.post.config;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FeignConfigTest {

    private final FeignConfig feignConfig = new FeignConfig();
    @Mock private HttpServletRequest request;

    @Test
    void requestInterceptor_forwardsHeaders() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("X-User-Id")).thenReturn("1");
        when(request.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(request.getHeader("X-User-Email")).thenReturn("test@test.com");

        RequestTemplate template = new RequestTemplate();
        feignConfig.requestInterceptor().apply(template);

        assertTrue(template.headers().get("X-User-Id").contains("1"));
        assertTrue(template.headers().get("X-User-Role").contains("ADMIN"));
        assertTrue(template.headers().get("X-User-Email").contains("test@test.com"));
        
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requestInterceptor_noAttributes() {
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate template = new RequestTemplate();
        assertDoesNotThrow(() -> feignConfig.requestInterceptor().apply(template));
        assertTrue(template.headers().isEmpty());
    }
}
