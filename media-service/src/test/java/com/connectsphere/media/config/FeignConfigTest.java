package com.connectsphere.media.config;

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
    void forwardsHeaders() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        when(req.getHeader("X-User-Id")).thenReturn("1");
        when(req.getHeader("X-User-Role")).thenReturn("USER");
        when(req.getHeader("X-User-Email")).thenReturn("a@b.com");

        RequestTemplate tpl = new RequestTemplate();
        feignConfig.requestInterceptor().apply(tpl);

        assertTrue(tpl.headers().get("X-User-Id").contains("1"));
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void noRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate tpl = new RequestTemplate();
        feignConfig.requestInterceptor().apply(tpl);
        assertTrue(tpl.headers().isEmpty());
    }
}
