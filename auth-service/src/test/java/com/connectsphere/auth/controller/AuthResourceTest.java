package com.connectsphere.auth.controller;

import com.connectsphere.auth.dto.*;
import com.connectsphere.auth.entity.OtpType;
import com.connectsphere.auth.entity.Role;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthResource.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthResource Controller — MockMvc Tests")
class AuthResourceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;
    @MockBean private com.connectsphere.auth.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private com.connectsphere.auth.security.OAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    @WithMockUser
    @DisplayName("POST /auth/register → 2xx success")
    void register_success() throws Exception {
        RegisterRequestDTO req = RegisterRequestDTO.builder()
                .username("vikash")
                .fullName("Vikash Prajapati")
                .email("vikash@test.com")
                .password("Password@123")
                .confirmPassword("Password@123") // <--- ADD THIS LINE
                .build();

        when(authService.register(any())).thenReturn(
                ApiResponseDTO.<String>builder().success(true).message("OTP sent").build());

        mockMvc.perform(post("/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is2xxSuccessful());
    }


    @Test
    @WithMockUser
    @DisplayName("POST /auth/login → 2xx success")
    void login_success() throws Exception {
        LoginRequestDTO req = LoginRequestDTO.builder()
                .email("vikash@test.com").password("Password@123").build();
        when(authService.login(any())).thenReturn(
                ApiResponseDTO.<LoginResponseDTO>builder().success(true)
                        .data(LoginResponseDTO.builder().accessToken("token").role(Role.USER).build()).build());
        mockMvc.perform(post("/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /auth/verify-otp → 200 OK")
    void verifyOtp_returns200() throws Exception {
        OtpVerifyRequestDTO req = OtpVerifyRequestDTO.builder()
                .email("vikash@test.com").otpCode("123456")
                .otpType(OtpType.EMAIL_VERIFICATION).build();
        when(authService.verifyOtp(any())).thenReturn(
                ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/auth/verify-otp").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /auth/forgot-password → 200 OK")
    void forgotPassword_returns200() throws Exception {
        when(authService.forgotPassword(anyString())).thenReturn(
                ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "vikash@test.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /auth/reset-password → 200 OK")
    void resetPassword_returns200() throws Exception {
        when(authService.resetPassword(anyString(), anyString())).thenReturn(
                ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "vikash@test.com", "newPassword", "New@1234"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /auth/refresh → 200 OK")
    void refreshToken_returns200() throws Exception {
        when(authService.refreshToken(anyString())).thenReturn(
                ApiResponseDTO.<LoginResponseDTO>builder().success(true)
                        .data(LoginResponseDTO.builder().accessToken("new_token").build()).build());
        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "old"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /auth/validate → 200 OK")
    void validateToken_returns200() throws Exception {
        when(authService.validateToken(anyString())).thenReturn(true);
        mockMvc.perform(get("/auth/validate")
                        .header("Authorization", "Bearer valid_token"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /auth/search → 200 OK")
    void searchUsers_returns200() throws Exception {
        when(authService.searchUsers("vikash")).thenReturn(
                ApiResponseDTO.<List<User>>builder().success(true).data(List.of()).build());
        mockMvc.perform(get("/auth/search").param("query", "vikash"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /auth/users/{id} → 200 OK")
    void getUserById_returns200() throws Exception {
        when(authService.getUserById(1)).thenReturn(
                ApiResponseDTO.<User>builder().success(true).data(User.builder().userId(1).build()).build());
        mockMvc.perform(get("/auth/users/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "vikash@test.com")
    @DisplayName("GET /auth/profile → 200 OK")
    void getProfile_returns200() throws Exception {
        when(authService.getUserByEmail("vikash@test.com")).thenReturn(
                ApiResponseDTO.<User>builder().success(true).data(User.builder().build()).build());
        mockMvc.perform(get("/auth/profile"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("GET /auth/admin/users → 200 OK for ADMIN")
    void getAllUsers_adminRole_returns200() throws Exception {
        when(authService.getAllUsers()).thenReturn(
                ApiResponseDTO.<List<User>>builder().success(true).data(List.of()).build());
        mockMvc.perform(get("/auth/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"MODERATOR"})
    @DisplayName("GET /auth/moderator/users/suspended → 200 OK")
    void getSuspendedUsers_returns200() throws Exception {
        when(authService.getSuspendedUsers()).thenReturn(
                ApiResponseDTO.<List<User>>builder().success(true).data(List.of()).build());
        mockMvc.perform(get("/auth/moderator/users/suspended"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /auth/resend-otp → 200 OK")
    void resendOtp_success() throws Exception {
        when(authService.resendOtp(anyString(), anyString())).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/auth/resend-otp")
                        .param("email", "test@test.com")
                        .param("otpType", "EMAIL_VERIFICATION")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /auth/logout → 200 OK")
    void logout_success() throws Exception {
        when(authService.logout(anyString())).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer some_token")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("PUT /auth/profile → 200 OK")
    void updateProfile_success() throws Exception {
        UpdateProfileRequestDTO req = new UpdateProfileRequestDTO();
        when(authService.updateProfileByEmail(anyString(), any())).thenReturn(ApiResponseDTO.<User>builder().success(true).build());
        mockMvc.perform(put("/auth/profile").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("PUT /auth/admin/users/{id}/deactivate → 200 OK")
    void deactivateUser_success() throws Exception {
        when(authService.deactivateUser(anyInt())).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(put("/auth/admin/users/1/deactivate").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /auth/internal/update-elite-status → 200 OK")
    void updateEliteStatus_success() throws Exception {
        when(authService.updateEliteStatus(anyInt(), anyBoolean(), any())).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(put("/auth/internal/update-elite-status")
                        .param("userId", "1")
                        .param("isElite", "true")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

}