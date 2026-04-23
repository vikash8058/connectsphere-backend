package com.connectsphere.auth.service;

import com.connectsphere.auth.dto.*;
import com.connectsphere.auth.entity.User;

import java.util.List;

/**
 * AuthService - Business Contract (Interface)
 *
 * Declares all operations as per ConnectSphere case study spec:
 *   register(), login(), logout(), validateToken(), refreshToken(),
 *   getUserByEmail(), getUserById(), updateProfile(), changePassword(),
 *   searchUsers(), deactivateAccount()
 *
 * Additional:
 *   verifyOtp(), resendOtp(), forgotPassword(), resetPassword(),
 *   reactivateUser(), getAllUsers(), getUsersByRole()
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

    // ─── ADMIN ───

    /**
     * Permanently delete a user account from the platform — ADMIN only.
     * Hard deletes the user row from the database.
     * Admin cannot delete their own account.
     */
    ApiResponseDTO<String> deleteUser(Integer adminId, Integer targetUserId);

    /**
     * Assign or change a user's role — ADMIN only.
     * Used to promote a USER to MODERATOR, or demote back.
     * Admin cannot change their own role.
     */
    ApiResponseDTO<String> assignRole(Integer adminId, Integer targetUserId, String role);

    // ─── MODERATOR ──

    /**
     * Get all suspended (inactive) user accounts.
     * Available to ADMIN and MODERATOR for moderation review.
     */
    ApiResponseDTO<List<User>> getSuspendedUsers();
}