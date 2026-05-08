package com.connectsphere.search.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

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
        request.addHeader("X-User-Role", "ADMIN");
        request.addHeader("X-User-Email", "test@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(123, request.getAttribute("requestingUserId"));
        assertEquals("ADMIN", request.getAttribute("requestingUserRole"));
    }

    @Test
    void doFilterInternal_missingHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNull(request.getAttribute("requestingUserId"));
        assertNull(request.getAttribute("requestingUserRole"));
    }

    @Test
    void doFilterInternal_invalidUserId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNull(request.getAttribute("requestingUserId"));
    }
}
