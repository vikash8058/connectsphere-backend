package com.connectsphere.post.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayHeaderFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private GatewayHeaderFilter gatewayHeaderFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withHeaders_setsAuthentication() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("1");
        when(request.getHeader("X-User-Role")).thenReturn("USER");
        when(request.getHeader("X-User-Email")).thenReturn("test@test.com");

        gatewayHeaderFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test@test.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(request).setAttribute("requestingUserId", 1);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withoutHeaders_skipsAuthentication() throws Exception {
        when(request.getHeader(anyString())).thenReturn(null);

        gatewayHeaderFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withInvalidId_handlesException() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("NOT_A_NUMBER");
        when(request.getHeader("X-User-Role")).thenReturn("USER");
        when(request.getHeader("X-User-Email")).thenReturn("test@test.com");

        gatewayHeaderFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
