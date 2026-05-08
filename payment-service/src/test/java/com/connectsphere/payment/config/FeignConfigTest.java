package com.connectsphere.payment.config;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignConfigTest {

    @Test
    void requestInterceptor_propagatesHeaders() {
        FeignConfig config = new FeignConfig();
        feign.RequestInterceptor interceptor = config.requestInterceptor();
        RequestTemplate template = new RequestTemplate();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn("123");
        when(request.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(request.getHeader("X-User-Email")).thenReturn("admin@test.com");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        interceptor.apply(template);

        assertTrue(template.headers().get("X-User-Id").contains("123"));
        assertTrue(template.headers().get("X-User-Role").contains("ADMIN"));
        assertTrue(template.headers().get("X-User-Email").contains("admin@test.com"));

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requestInterceptor_noContext() {
        FeignConfig config = new FeignConfig();
        feign.RequestInterceptor interceptor = config.requestInterceptor();
        RequestTemplate template = new RequestTemplate();

        RequestContextHolder.resetRequestAttributes();
        interceptor.apply(template);

        assertTrue(template.headers().isEmpty());
    }
}
