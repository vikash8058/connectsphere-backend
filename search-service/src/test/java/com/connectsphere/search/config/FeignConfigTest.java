package com.connectsphere.search.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeignConfigTest {

    @Test
    void requestInterceptor_propagatesHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        FeignConfig config = new FeignConfig();
        RequestTemplate template = new RequestTemplate();

        config.requestInterceptor().apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.containsKey("X-User-Id"));
        assertEquals("123", headers.get("X-User-Id").iterator().next());

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requestInterceptor_noRequest() {
        RequestContextHolder.resetRequestAttributes();
        FeignConfig config = new FeignConfig();
        RequestTemplate template = new RequestTemplate();

        config.requestInterceptor().apply(template);

        assertTrue(template.headers().isEmpty());
    }
}
