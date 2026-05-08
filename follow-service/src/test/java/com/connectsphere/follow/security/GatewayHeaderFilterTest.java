package com.connectsphere.follow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayHeaderFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @InjectMocks private GatewayHeaderFilter gatewayHeaderFilter;

    @Test
    void doFilterInternal_withAllHeaders() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("1");
        when(request.getHeader("X-User-Role")).thenReturn("USER");
        when(request.getHeader("X-User-Email")).thenReturn("test@test.com");

        gatewayHeaderFilter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute("requestingUserId", 1);
        verify(request).setAttribute("requestingUserRole", "USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_missingHeaders() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-User-Role")).thenReturn(null);
        when(request.getHeader("X-User-Email")).thenReturn(null);

        gatewayHeaderFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void doFilterInternal_invalidUserId() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("INVALID");
        when(request.getHeader("X-User-Role")).thenReturn("USER");
        when(request.getHeader("X-User-Email")).thenReturn("test@test.com");

        gatewayHeaderFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }
}
