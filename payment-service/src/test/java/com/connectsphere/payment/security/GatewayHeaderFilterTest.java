package com.connectsphere.payment.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewayHeaderFilterTest {

    private GatewayHeaderFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayHeaderFilter();
    }

    @Test
    void doFilterInternal_validHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "123");
        request.addHeader("X-User-Role", "USER");
        request.addHeader("X-User-Email", "test@example.com");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(123, request.getAttribute("requestingUserId"));
        assertEquals("USER", request.getAttribute("requestingUserRole"));
    }

    @Test
    void doFilterInternal_invalidUserId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "invalid");
        request.addHeader("X-User-Role", "USER");
        request.addHeader("X-User-Email", "test@example.com");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNull(request.getAttribute("requestingUserId"));
    }

    @Test
    void doFilterInternal_missingHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNull(request.getAttribute("requestingUserId"));
    }

    @Test
    void doFilterInternal_malformedHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Use malformed string to trigger the parse exception branch
        request.addHeader("X-User-Id", "not-a-number");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNull(request.getAttribute("requestingUserId"));
    }
}
