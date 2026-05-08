package com.connectsphere.auth.security;

import com.connectsphere.auth.entity.Role;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private OAuth2AuthenticationToken authentication;

    @Mock
    private OAuth2User oAuth2User;

    @InjectMocks
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(oAuth2SuccessHandler, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void testOnAuthenticationSuccess_ExistingUser() throws IOException, ServletException {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn("test@gmail.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Test User");

        User existingUser = User.builder()
                .userId(1)
                .email("test@gmail.com")
                .username("testuser")
                .role(Role.USER)
                .isActive(true)
                .build();

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(existingUser));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(anyString());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testOnAuthenticationSuccess_NewUser() throws IOException, ServletException {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("github");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);

        // Add these three stubs to satisfy the code's requirements
        when(oAuth2User.getAttribute("email")).thenReturn(null);
        when(oAuth2User.getAttribute("name")).thenReturn("GitHub User"); // ADD THIS LINE
        when(oAuth2User.getAttribute("login")).thenReturn("githubuser");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        User newUser = User.builder()
                .userId(2)
                .email("githubuser@github-noreply.com")
                .username("githubuser_123")
                .role(Role.USER)
                .isActive(true)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(newUser);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);
        verify(response).sendRedirect(anyString());
    }

}
