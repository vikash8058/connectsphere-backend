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

    @Override
    @Transactional
    public ApiResponseDTO<String> register(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username taken");
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
                .isElite(false)
                .build();
        userRepository.save(user);
        sendOtpToEmail(request.getEmail(), OtpType.EMAIL_VERIFICATION);
        return ApiResponseDTO.success("Registration successful! Verify OTP.");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> verifyOtp(OtpVerifyRequestDTO request) {
        if (request.getOtpType() == OtpType.PASSWORD_RESET) {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
            user.setIsPasswordResetVerified(true);
            userRepository.save(user);
            return ApiResponseDTO.success("OTP verified.");
        }
        OtpVerification otp = otpVerificationRepository.findValidOtp(request.getEmail(), request.getOtpType())
                .orElseThrow(() -> new InvalidOtpException("Invalid OTP"));
        if (!otp.getOtpCode().equals(request.getOtpCode()))
            throw new InvalidOtpException("Wrong OTP");
        otpVerificationRepository.markAsUsed(otp.getOtpId());
        if (request.getOtpType() == OtpType.EMAIL_VERIFICATION) {
            userRepository.markEmailVerified(request.getEmail());
        }
        return ApiResponseDTO.success("Verified successfully.");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> resendOtp(String email, String otpType) {
        if (!userRepository.existsByEmail(email))
            throw new UserNotFoundException("Not found");
        otpVerificationRepository.deleteAllByEmailAndOtpType(email, OtpType.valueOf(otpType));
        sendOtpToEmail(email, OtpType.valueOf(otpType));
        return ApiResponseDTO.success("OTP resent.");
    }

    @Override
    @Transactional
    public ApiResponseDTO<LoginResponseDTO> login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email/pass"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
            throw new InvalidCredentialsException("Invalid email/pass");
        if (!user.getIsActive())
            throw new InvalidCredentialsException("Your account has been suspended by an administrator.");
        if (!user.getIsEmailVerified())
            throw new InvalidCredentialsException("Verify email");
        String access = jwtTokenProvider.generateAccessToken(user);
        String refresh = jwtTokenProvider.generateRefreshToken(user);
        userRepository.updateLastLoginAt(user.getUserId(), LocalDateTime.now());
        return ApiResponseDTO.success("Login successful", buildLoginResponse(user, access, refresh));
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> logout(String token) {
        if (blacklistedTokenRepository.existsByToken(token))
            return ApiResponseDTO.success("Logged out");
        BlacklistedToken bt = new BlacklistedToken();
        bt.setToken(token);
        bt.setExpiryDate(LocalDateTime.now().plusHours(1)); // Simple default
        blacklistedTokenRepository.save(bt);
        return ApiResponseDTO.success("Logged out");
    }

    @Override
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token) && !blacklistedTokenRepository.existsByToken(token);
    }

    @Override
    public ApiResponseDTO<LoginResponseDTO> refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken))
            throw new InvalidCredentialsException("Invalid refresh");
        User user = userRepository.findByEmail(jwtTokenProvider.getEmailFromToken(refreshToken))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ApiResponseDTO.success("Refreshed",
                buildLoginResponse(user, jwtTokenProvider.generateAccessToken(user), refreshToken));
    }

    @Override
    public ApiResponseDTO<User> getUserById(Integer userId) {
        return ApiResponseDTO.success("Fetched",
                userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("Not found")));
    }

    @Override
    public ApiResponseDTO<User> getUserByEmail(String email) {
        return ApiResponseDTO.success("Fetched",
                userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Not found")));
    }

    @Override
    @Transactional
    public ApiResponseDTO<User> updateProfileByEmail(String email, UpdateProfileRequestDTO request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Not found"));
        if (request.getUsername() != null)
            user.setUsername(request.getUsername());
        if (request.getFullName() != null)
            user.setFullName(request.getFullName());
        if (request.getBio() != null)
            user.setBio(request.getBio());
        if (request.getProfilePicUrl() != null)
            user.setProfilePicUrl(request.getProfilePicUrl());
        return ApiResponseDTO.success("Updated", userRepository.save(user));
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> changePassword(String email, ChangePasswordRequestDTO request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash()))
            throw new InvalidCredentialsException("Wrong pass");
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return ApiResponseDTO.success("Changed");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> setInitialPassword(String email, SetPasswordRequestDTO request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Not found"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return ApiResponseDTO.success("Set");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> deactivateUser(Integer userId) {
        userRepository.deactivateByUserId(userId);
        return ApiResponseDTO.success("Deactivated");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> reactivateUser(Integer userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("Not found"));
        user.setIsActive(true);
        userRepository.save(user);
        return ApiResponseDTO.success("Reactivated");
    }

    @Override
    public ApiResponseDTO<List<User>> getAllUsers() {
        return ApiResponseDTO.success("Fetched", userRepository.findAll());
    }

    @Override
    public ApiResponseDTO<List<User>> getUsersByRole(String role) {
        return ApiResponseDTO.success("Fetched", userRepository.findAllByRole(Role.valueOf(role.toUpperCase())));
    }

    @Override
    public ApiResponseDTO<List<User>> searchUsers(String query) {
        return ApiResponseDTO.success("Results", userRepository.searchByUsername(query));
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> deleteUser(Integer adminId, Integer targetUserId) {
        userRepository.deleteByUserId(targetUserId);
        return ApiResponseDTO.success("Deleted");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> assignRole(Integer adminId, Integer targetUserId, String role) {
        userRepository.updateRoleByUserId(targetUserId, Role.valueOf(role.toUpperCase()));
        return ApiResponseDTO.success("Role assigned");
    }

    @Override
    public ApiResponseDTO<List<User>> getSuspendedUsers() {
        return ApiResponseDTO.success("Fetched", userRepository.findByIsActive(false));
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> forgotPassword(String email) {
        sendOtpToEmail(email, OtpType.PASSWORD_RESET);
        return ApiResponseDTO.success("OTP sent.");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsPasswordResetVerified(false);
        userRepository.save(user);
        return ApiResponseDTO.success("Reset successful.");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> updateEliteStatus(Integer userId, Boolean isElite, String eliteUntil) {
        log.info("Updating elite status for user: {} to {} until {}", userId, isElite, eliteUntil);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        user.setIsElite(isElite);
        if (eliteUntil != null && !eliteUntil.isBlank()) {
            try {
                // Remove potential quotes or encoding issues
                String cleanDate = eliteUntil.replace("\"", "");
                user.setEliteUntil(LocalDateTime.parse(cleanDate));
            } catch (Exception e) {
                log.warn("Failed to parse eliteUntil date: {}. Using default 30 days.", eliteUntil);
                user.setEliteUntil(LocalDateTime.now().plusDays(30));
            }
        }
        userRepository.save(user);
        return ApiResponseDTO.success("Elite status updated.");
    }

    private void sendOtpToEmail(String email, OtpType type) {
        String code = OtpUtil.generateOtp();
        otpVerificationRepository.save(OtpVerification.builder().email(email).otpCode(code).otpType(type)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes)).isUsed(false).build());
        emailService.sendOtpEmail(email, code, type);
    }

    private LoginResponseDTO buildLoginResponse(User user, String access, String refresh) {
        return LoginResponseDTO.builder()
                .accessToken(access).refreshToken(refresh).tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .userId(user.getUserId()).username(user.getUsername()).fullName(user.getFullName())
                .email(user.getEmail()).bio(user.getBio()).profilePicUrl(user.getProfilePicUrl())
                .role(user.getRole()).provider(user.getProvider())
                .isPasswordSet(user.getPasswordHash() != null)
                .isElite(user.getIsElite())
                .eliteUntil(user.getEliteUntil())
                .build();
    }
}