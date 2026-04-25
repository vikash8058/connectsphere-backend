package com.connectsphere.auth.service;

import com.connectsphere.auth.dto.*;
import com.connectsphere.auth.entity.User;

import java.util.List;

/**
 * AuthService - Business Contract (Interface)
 */
public interface AuthService {

    ApiResponseDTO<String> register(RegisterRequestDTO request);
    ApiResponseDTO<String> verifyOtp(OtpVerifyRequestDTO request);
    ApiResponseDTO<String> resendOtp(String email, String otpType);
    ApiResponseDTO<LoginResponseDTO> login(LoginRequestDTO request);
    ApiResponseDTO<String> logout(String token);
    boolean validateToken(String token);
    ApiResponseDTO<LoginResponseDTO> refreshToken(String refreshToken);
    ApiResponseDTO<User> getUserById(Integer userId);
    ApiResponseDTO<User> getUserByEmail(String email);
    ApiResponseDTO<User> updateProfileByEmail(String email, UpdateProfileRequestDTO request);
    ApiResponseDTO<String> changePassword(String email, ChangePasswordRequestDTO request);
    ApiResponseDTO<String> setInitialPassword(String email, SetPasswordRequestDTO request);
    ApiResponseDTO<String> deactivateUser(Integer userId);
    ApiResponseDTO<String> reactivateUser(Integer userId);
    ApiResponseDTO<List<User>> getAllUsers();
    ApiResponseDTO<List<User>> getUsersByRole(String role);
    ApiResponseDTO<List<User>> searchUsers(String query);
    ApiResponseDTO<String> forgotPassword(String email);
    ApiResponseDTO<String> resetPassword(String email, String newPassword);
    ApiResponseDTO<String> deleteUser(Integer adminId, Integer targetUserId);
    ApiResponseDTO<String> assignRole(Integer adminId, Integer targetUserId, String role);
    ApiResponseDTO<List<User>> getSuspendedUsers();

    // Internal
    ApiResponseDTO<String> updateEliteStatus(Integer userId, Boolean isElite, String eliteUntil);
}