package com.connectsphere.like.config;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeignConfigTest {

    private final FeignConfig feignConfig = new FeignConfig();

    @Test
    void requestInterceptor_forwardsHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("X-User-Id")).thenReturn("1");
        when(request.getHeader("X-User-Role")).thenReturn("USER");
        when(request.getHeader("X-User-Email")).thenReturn("a@b.com");

        RequestTemplate template = new RequestTemplate();
        feignConfig.requestInterceptor().apply(template);

        assertTrue(template.headers().get("X-User-Id").contains("1"));
        assertTrue(template.headers().get("X-User-Role").contains("USER"));
        assertTrue(template.headers().get("X-User-Email").contains("a@b.com"));
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requestInterceptor_noRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate template = new RequestTemplate();
        feignConfig.requestInterceptor().apply(template);
        assertTrue(template.headers().isEmpty());
    }
}
