package com.connectsphere.auth.service;

import com.connectsphere.auth.dto.*;
import com.connectsphere.auth.entity.*;
import com.connectsphere.auth.exception.*;
import com.connectsphere.auth.repository.BlacklistedTokenRepository;
import com.connectsphere.auth.repository.OtpVerificationRepository;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtTokenProvider;
import com.connectsphere.auth.util.OtpUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * AuthServiceImpl - Business Logic Implementation
 *
 * Key flows:
 * 1. Registration  -> Validate unique email/username -> Hash password
 *                  -> Save user (unverified) -> Send OTP
 * 2. OTP Verify   -> Find valid OTP -> Match code -> Mark verified
 * 3. Login        -> Validate credentials -> Check active+verified -> Issue JWT
 * 4. Refresh      -> Validate refresh token -> Issue new access token
 * 5. Search       -> LIKE query on username / fullName
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Value("${otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    // REGISTRATION

    @Override
    @Transactional
    public ApiResponseDTO<String> register(RegisterRequestDTO request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "An account with email " + request.getEmail() + " already exists");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(
                    "Username '" + request.getUsername() + "' is already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .isEmailVerified(false)
                .build();

        userRepository.save(user);
        log.info("User saved: {}", request.getEmail());

        sendOtpToEmail(request.getEmail(), OtpType.EMAIL_VERIFICATION);

        return ApiResponseDTO.success(
                "Registration successful! Please check your email for OTP verification.");
    }

    // OTP VERIFICATION

    @Override
    @Transactional
    public ApiResponseDTO<String> verifyOtp(OtpVerifyRequestDTO request) {
        log.info("OTP verification for email: {}", request.getEmail());
        
        if (request.getOtpType() == OtpType.PASSWORD_RESET) {

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            user.setIsPasswordResetVerified(true);
            userRepository.save(user);

            return ApiResponseDTO.success("OTP verified. You can now reset your password.");
        }

        OtpVerification otp = otpVerificationRepository
                .findValidOtp(request.getEmail(), request.getOtpType())
                .orElseThrow(() -> new InvalidOtpException("OTP is invalid or has expired"));

        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            throw new InvalidOtpException("Invalid OTP. Please try again.");
        }

        otpVerificationRepository.markAsUsed(otp.getOtpId());

        if (request.getOtpType() == OtpType.EMAIL_VERIFICATION) {
            userRepository.markEmailVerified(request.getEmail());
            log.info("Email verified for: {}", request.getEmail());
            return ApiResponseDTO.success("Email verified successfully! You can now login.");
        }

        return ApiResponseDTO.success("OTP verified. You can now reset your password.");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> resendOtp(String email, String otpType) {
        log.info("OTP resend for: {}", email);
        if (!userRepository.existsByEmail(email)) {
            throw new UserNotFoundException("No account found with email: " + email);
        }
        OtpType type = OtpType.valueOf(otpType);
        otpVerificationRepository.deleteAllByEmailAndOtpType(email, type);
        sendOtpToEmail(email, type);
        return ApiResponseDTO.success("A new OTP has been sent to your email.");
    }

    // LOGIN

    @Override
    @Transactional
    public ApiResponseDTO<LoginResponseDTO> login(LoginRequestDTO request) {
        log.info("Login attempt for: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.getIsActive()) {
            throw new InvalidCredentialsException(
                    "Your account has been suspended. Please contact support.");
        }

        if (!user.getIsEmailVerified()) {
            throw new InvalidCredentialsException(
                    "Please verify your email first. Check your inbox for the OTP.");
        }

        String accessToken  = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        userRepository.updateLastLoginAt(user.getUserId(), LocalDateTime.now());
        log.info("Login successful for: {}", request.getEmail());

        return ApiResponseDTO.success("Login successful", buildLoginResponse(user, accessToken, refreshToken));
    }


    // TOKEN MANAGEMENT

    @Override
    @Transactional
    public ApiResponseDTO<String> logout(String token) {
        log.info("Logout requested");

        // Token already blacklisted → silently succeed
        if (blacklistedTokenRepository.existsByToken(token)) {
            return ApiResponseDTO.success("Already logged out");
        }

        // Extract token expiry; fall back to 1 hour if extraction fails
        LocalDateTime expiryDate;
        try {
            Date expiry = jwtTokenProvider.extractExpiration(token);
            expiryDate = expiry.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("Token expiry extraction failed, defaulting to 1h: {}", e.getMessage());
            expiryDate = LocalDateTime.now().plusHours(1);
        }

        BlacklistedToken blacklistedToken = new BlacklistedToken();
        blacklistedToken.setToken(token);
        blacklistedToken.setExpiryDate(expiryDate);

        blacklistedTokenRepository.save(blacklistedToken);
        log.info("Token blacklisted — logout successful");

        return ApiResponseDTO.success("Logged out successfully");
    }

    @Override
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token)
                && !blacklistedTokenRepository.existsByToken(token);
    }

    @Override
    public ApiResponseDTO<LoginResponseDTO> refreshToken(String refreshToken) {
        log.info("Token refresh requested");
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidCredentialsException("Refresh token is invalid or expired");
        }
        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        return ApiResponseDTO.success("Token refreshed successfully",
                buildLoginResponse(user, newAccessToken, refreshToken));
    }

    // USER MANAGEMENT
  
    @Override
    public ApiResponseDTO<User> getUserById(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
        return ApiResponseDTO.success("User fetched successfully", user);
    }

    @Override
    public ApiResponseDTO<User> getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return ApiResponseDTO.success("User fetched successfully", user);
    }

    @Override
    @Transactional
    public ApiResponseDTO<User> updateProfileByEmail(String email, UpdateProfileRequestDTO request) {
        log.info("Profile update for user email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (request.getUsername() != null) {
            if (!request.getUsername().equals(user.getUsername())
                    && userRepository.existsByUsername(request.getUsername())) {
                throw new UserAlreadyExistsException(
                        "Username '" + request.getUsername() + "' is already taken");
            }
            user.setUsername(request.getUsername());
        }
        if (request.getFullName() != null)    user.setFullName(request.getFullName());
        if (request.getBio() != null)         user.setBio(request.getBio());
        if (request.getProfilePicUrl() != null) user.setProfilePicUrl(request.getProfilePicUrl());

        User updated = userRepository.save(user);
        return ApiResponseDTO.success("Profile updated successfully", updated);
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> changePassword(String email, ChangePasswordRequestDTO request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // check current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        // check new == confirm
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        // update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ApiResponseDTO.success("Password changed successfully");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> deactivateUser(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found with ID: " + userId);
        }
        userRepository.deactivateByUserId(userId);
        return ApiResponseDTO.success("User account suspended successfully");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> reactivateUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
        user.setIsActive(true);
        userRepository.save(user);
        return ApiResponseDTO.success("User account reactivated successfully");
    }

    @Override
    public ApiResponseDTO<List<User>> getAllUsers() {
        return ApiResponseDTO.success("Users fetched successfully", userRepository.findAll());
    }

    @Override
    public ApiResponseDTO<List<User>> getUsersByRole(String role) {
        Role userRole = Role.valueOf(role.toUpperCase());
        return ApiResponseDTO.success("Users fetched successfully",
                userRepository.findAllByRole(userRole));
    }

    /**
     * Search users by username or full name — used by the global search feature
     * and the "Discover users" / @mention autocomplete
     */
    @Override
    public ApiResponseDTO<List<User>> searchUsers(String query) {
        log.debug("Searching users with query: {}", query);
        List<User> results = userRepository.searchByUsername(query);
        return ApiResponseDTO.success("Search results", results);
    }

    // ─── ADMIN ─────

    /**
     * Permanently deletes a user account — ADMIN only.
     *
     * Business rules:
     *  1. Target user must exist.
     *  2. Admin cannot delete their own account (self-protection).
     *
     * This is a hard delete (removes the row). For soft-delete use deactivateUser().
     */
    @Override
    @Transactional
    public ApiResponseDTO<String> deleteUser(Integer adminId, Integer targetUserId) {
        log.info("Admin {} permanently deleting user {}", adminId, targetUserId);

        if (adminId.equals(targetUserId)) {
            throw new UnauthorizedAccessException(
                    "Admin cannot permanently delete their own account.");
        }

        if (!userRepository.existsById(targetUserId)) {
            throw new UserNotFoundException("User not found with ID: " + targetUserId);
        }

        userRepository.deleteByUserId(targetUserId);
        log.info("User {} permanently deleted by admin {}", targetUserId, adminId);
        return ApiResponseDTO.success("User account permanently deleted.");
    }

    /**
     * Assigns or changes a user's role — ADMIN only.
     *
     * Business rules:
     *  1. Target user must exist.
     *  2. Admin cannot change their own role.
     *  3. Role must be a valid Role enum value (USER, ADMIN, MODERATOR).
     *
     * Use case: Promote a USER to MODERATOR, or demote MODERATOR back to USER.
     */
    @Override
    @Transactional
    public ApiResponseDTO<String> assignRole(Integer adminId, Integer targetUserId, String roleName) {
        log.info("Admin {} assigning role {} to user {}", adminId, roleName, targetUserId);

        if (adminId.equals(targetUserId)) {
            throw new UnauthorizedAccessException(
                    "Admin cannot change their own role.");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with ID: " + targetUserId));

        Role newRole;
        try {
            newRole = Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid role '" + roleName + "'. Must be one of: USER, ADMIN, MODERATOR");
        }

        userRepository.updateRoleByUserId(targetUserId, newRole);
        log.info("Role of user {} changed to {} by admin {}", targetUserId, newRole, adminId);
        return ApiResponseDTO.success(
                "Role of user '" + target.getUsername() + "' updated to " + newRole + ".");
    }

    // ─── MODERATOR ────

    /**
     * Returns all suspended (inactive) user accounts.
     * Available to ADMIN and MODERATOR.
     * Used during content/account moderation review.
     */
    @Override
    public ApiResponseDTO<List<User>> getSuspendedUsers() {
        List<User> suspended = userRepository.findByIsActive(false);
        return ApiResponseDTO.success(
                "Suspended users fetched successfully. Total: " + suspended.size(), suspended);
    }

    // PASSWORD RESET


    @Override
    @Transactional
    public ApiResponseDTO<String> forgotPassword(String email) {
        log.info("Forgot password for: {}", email);
        if (!userRepository.existsByEmail(email)) {
            // Security: don't reveal whether the email is registered
            return ApiResponseDTO.success("If this email is registered, an OTP will be sent.");
        }
        otpVerificationRepository.deleteAllByEmailAndOtpType(email, OtpType.PASSWORD_RESET);
        sendOtpToEmail(email, OtpType.PASSWORD_RESET);
        return ApiResponseDTO.success("OTP sent to your email for password reset.");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> resetPassword(String email, String newPassword) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // CHECK OTP VERIFICATION
        if (!Boolean.TRUE.equals(user.getIsPasswordResetVerified())) {
            throw new InvalidOtpException("OTP verification required before resetting password");
        }

        //RESET PASSWORD
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        //RESET FLAG (IMPORTANT)
        user.setIsPasswordResetVerified(false);

        userRepository.save(user);

        return ApiResponseDTO.success("Password reset successfully. You can now login.");
    }

    // PRIVATE HELPERS
 
    private void sendOtpToEmail(String email, OtpType otpType) {
        String otpCode = OtpUtil.generateOtp();
        OtpVerification otpVerification = OtpVerification.builder()
                .email(email)
                .otpCode(otpCode)
                .otpType(otpType)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .isUsed(false)
                .build();
        otpVerificationRepository.save(otpVerification);
        emailService.sendOtpEmail(email, otpCode, otpType);
        log.info("OTP sent to: {}", email);
    }

    private LoginResponseDTO buildLoginResponse(User user, String accessToken, String refreshToken) {
        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .bio(user.getBio())
                .profilePicUrl(user.getProfilePicUrl())
                .role(user.getRole())
                .provider(user.getProvider())
                .build();
    }
}