package com.connectsphere.auth.service;

import com.connectsphere.auth.dto.*;
import com.connectsphere.auth.entity.*;
import com.connectsphere.auth.exception.*;
import com.connectsphere.auth.repository.BlacklistedTokenRepository;
import com.connectsphere.auth.repository.OtpVerificationRepository;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — Full Unit Tests")
class AuthServiceImplTest {

    @Mock private UserRepository             userRepository;
    @Mock private OtpVerificationRepository  otpRepository;
    @Mock private BlacklistedTokenRepository blacklistedTokenRepository;
    @Mock private PasswordEncoder            passwordEncoder;
    @Mock private JwtTokenProvider           jwtTokenProvider;
    @Mock private EmailService               emailService;

    @InjectMocks private AuthServiceImpl authService;

    private User regularUser;
    private User adminUser;
    private User moderatorUser;
    private User suspendedUser;
    private RegisterRequestDTO registerDTO;
    private LoginRequestDTO    loginDTO;

    @BeforeEach
    void setup() {
        regularUser = User.builder()
                .userId(1).username("vikash").fullName("Vikash Prajapati")
                .email("vikash@test.com").passwordHash("hashed")
                .role(Role.USER).provider(AuthProvider.LOCAL)
                .isActive(true).isEmailVerified(true)
                .isPasswordResetVerified(false)
                .createdAt(LocalDateTime.now()).build();

        adminUser = User.builder()
                .userId(2).username("admin_vikash").fullName("Admin User")
                .email("admin@test.com").passwordHash("hashed")
                .role(Role.ADMIN).provider(AuthProvider.LOCAL)
                .isActive(true).isEmailVerified(true)
                .isPasswordResetVerified(false)
                .createdAt(LocalDateTime.now()).build();

        moderatorUser = User.builder()
                .userId(3).username("mod_vikash").fullName("Moderator User")
                .email("mod@test.com").passwordHash("hashed")
                .role(Role.MODERATOR).provider(AuthProvider.LOCAL)
                .isActive(true).isEmailVerified(true)
                .isPasswordResetVerified(false)
                .createdAt(LocalDateTime.now()).build();

        suspendedUser = User.builder()
                .userId(4).username("banned_user").fullName("Banned Person")
                .email("banned@test.com").passwordHash("hashed")
                .role(Role.USER).provider(AuthProvider.LOCAL)
                .isActive(false).isEmailVerified(true)
                .isPasswordResetVerified(false)
                .createdAt(LocalDateTime.now()).build();

        registerDTO = RegisterRequestDTO.builder()
                .username("vikash").fullName("Vikash Prajapati")
                .email("vikash@test.com").password("Password@123").build();

        loginDTO = LoginRequestDTO.builder()
                .email("vikash@test.com").password("Password@123").build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. REGISTER
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Register")
    class RegisterTests {

        @Test @DisplayName("Success — new user saved, OTP sent")
        void register_success() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(regularUser);
            doNothing().when(emailService).sendOtpEmail(anyString(), anyString(), any());

            ApiResponseDTO<String> resp = authService.register(registerDTO);

            assertTrue(resp.isSuccess());
            verify(userRepository).save(any(User.class));
            verify(otpRepository).save(any(OtpVerification.class));
            verify(emailService).sendOtpEmail(anyString(), anyString(), eq(OtpType.EMAIL_VERIFICATION));
        }

        @Test @DisplayName("Fail — email already exists")
        void register_emailExists() {
            when(userRepository.existsByEmail(anyString())).thenReturn(true);
            assertThrows(UserAlreadyExistsException.class, () -> authService.register(registerDTO));
            verify(userRepository, never()).save(any());
        }

        @Test @DisplayName("Fail — username already taken")
        void register_usernameExists() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername(anyString())).thenReturn(true);
            assertThrows(UserAlreadyExistsException.class, () -> authService.register(registerDTO));
            verify(userRepository, never()).save(any());
        }

        @Test @DisplayName("Verify — new user always gets role=USER and provider=LOCAL")
        void register_defaultRoleAndProvider() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendOtpEmail(anyString(), anyString(), any());

            authService.register(registerDTO);

            verify(userRepository).save(argThat(u ->
                    u.getRole() == Role.USER
                    && u.getProvider() == AuthProvider.LOCAL
                    && Boolean.FALSE.equals(u.getIsEmailVerified())
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. LOGIN
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Login")
    class LoginTests {

        @Test @DisplayName("Success — USER login returns role=USER in response")
        void login_userSuccess() {
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(regularUser)).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(regularUser)).thenReturn("refresh");
            when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(86400000L);

            ApiResponseDTO<LoginResponseDTO> resp = authService.login(loginDTO);

            assertTrue(resp.isSuccess());
            assertEquals(Role.USER, resp.getData().getRole());
            assertEquals("access", resp.getData().getAccessToken());
        }

        @Test @DisplayName("Success — ADMIN login returns role=ADMIN in response")
        void login_adminSuccess() {
            LoginRequestDTO req = LoginRequestDTO.builder()
                    .email("admin@test.com").password("Admin@123").build();
            when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(adminUser)).thenReturn("admin_token");
            when(jwtTokenProvider.generateRefreshToken(adminUser)).thenReturn("admin_refresh");
            when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(86400000L);

            ApiResponseDTO<LoginResponseDTO> resp = authService.login(req);

            assertEquals(Role.ADMIN, resp.getData().getRole());
        }

        @Test @DisplayName("Success — MODERATOR login returns role=MODERATOR in response")
        void login_moderatorSuccess() {
            LoginRequestDTO req = LoginRequestDTO.builder()
                    .email("mod@test.com").password("Mod@123").build();
            when(userRepository.findByEmail("mod@test.com")).thenReturn(Optional.of(moderatorUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(moderatorUser)).thenReturn("mod_token");
            when(jwtTokenProvider.generateRefreshToken(moderatorUser)).thenReturn("mod_refresh");
            when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(86400000L);

            ApiResponseDTO<LoginResponseDTO> resp = authService.login(req);

            assertEquals(Role.MODERATOR, resp.getData().getRole());
        }

        @Test @DisplayName("Fail — wrong password")
        void login_wrongPassword() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(regularUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
            assertThrows(InvalidCredentialsException.class, () -> authService.login(loginDTO));
        }

        @Test @DisplayName("Fail — user not found")
        void login_userNotFound() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            assertThrows(InvalidCredentialsException.class, () -> authService.login(loginDTO));
        }

        @Test @DisplayName("Fail — suspended user cannot login")
        void login_suspendedUser() {
            LoginRequestDTO req = LoginRequestDTO.builder()
                    .email("banned@test.com").password("Pass@123").build();
            when(userRepository.findByEmail("banned@test.com")).thenReturn(Optional.of(suspendedUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            assertThrows(InvalidCredentialsException.class, () -> authService.login(req));
        }

        @Test @DisplayName("Fail — email not verified")
        void login_emailNotVerified() {
            regularUser.setIsEmailVerified(false);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(regularUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            assertThrows(InvalidCredentialsException.class, () -> authService.login(loginDTO));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. OTP
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("OTP Verification")
    class OtpTests {

        @Test @DisplayName("Success — correct OTP marks email verified")
        void verifyOtp_success() {
            OtpVerifyRequestDTO req = OtpVerifyRequestDTO.builder()
                    .email("vikash@test.com").otpCode("123456")
                    .otpType(OtpType.EMAIL_VERIFICATION).build();
            OtpVerification otp = OtpVerification.builder()
                    .otpId(1).email("vikash@test.com").otpCode("123456")
                    .otpType(OtpType.EMAIL_VERIFICATION)
                    .expiresAt(LocalDateTime.now().plusMinutes(5)).isUsed(false).build();

            when(otpRepository.findValidOtp(anyString(), any())).thenReturn(java.util.Optional.of(otp));

            ApiResponseDTO<String> resp = authService.verifyOtp(req);

            assertTrue(resp.isSuccess());
            verify(otpRepository).markAsUsed(1);
            verify(userRepository).markEmailVerified("vikash@test.com");
        }

        @Test @DisplayName("Fail — wrong OTP code")
        void verifyOtp_wrongCode() {
            OtpVerifyRequestDTO req = OtpVerifyRequestDTO.builder()
                    .email("vikash@test.com").otpCode("999999")
                    .otpType(OtpType.EMAIL_VERIFICATION).build();
            OtpVerification otp = OtpVerification.builder()
                    .otpCode("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(5)).isUsed(false).build();

            when(otpRepository.findValidOtp(anyString(), any())).thenReturn(java.util.Optional.of(otp));

            assertThrows(InvalidOtpException.class, () -> authService.verifyOtp(req));
        }

        @Test @DisplayName("Fail — OTP expired or not found")
        void verifyOtp_expired() {
            OtpVerifyRequestDTO req = OtpVerifyRequestDTO.builder()
                    .email("vikash@test.com").otpCode("123456")
                    .otpType(OtpType.EMAIL_VERIFICATION).build();
            when(otpRepository.findValidOtp(anyString(), any())).thenReturn(Optional.empty());
            assertThrows(InvalidOtpException.class, () -> authService.verifyOtp(req));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. PASSWORD
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Password Management")
    class PasswordTests {

        @Test @DisplayName("changePassword — success")
        void changePassword_success() {
            ChangePasswordRequestDTO req = ChangePasswordRequestDTO.builder()
                    .currentPassword("oldPass").newPassword("NewPass@1")
                    .confirmPassword("NewPass@1").build();
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            when(passwordEncoder.matches("oldPass", "hashed")).thenReturn(true);
            when(passwordEncoder.encode("NewPass@1")).thenReturn("newHash");

            ApiResponseDTO<String> resp = authService.changePassword("vikash@test.com", req);

            assertTrue(resp.isSuccess());
            verify(userRepository).save(argThat(u -> "newHash".equals(u.getPasswordHash())));
        }

        @Test @DisplayName("changePassword — fail: wrong current password")
        void changePassword_wrongCurrent() {
            ChangePasswordRequestDTO req = ChangePasswordRequestDTO.builder()
                    .currentPassword("wrongPass").newPassword("New@1").confirmPassword("New@1").build();
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            when(passwordEncoder.matches("wrongPass", "hashed")).thenReturn(false);
            assertThrows(InvalidCredentialsException.class,
                    () -> authService.changePassword("vikash@test.com", req));
        }

        @Test @DisplayName("changePassword — fail: passwords do not match")
        void changePassword_mismatch() {
            ChangePasswordRequestDTO req = ChangePasswordRequestDTO.builder()
                    .currentPassword("old").newPassword("New@1").confirmPassword("Diff@2").build();
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            assertThrows(IllegalArgumentException.class,
                    () -> authService.changePassword("vikash@test.com", req));
        }

        @Test @DisplayName("forgotPassword — sends OTP for known email")
        void forgotPassword_success() {
            when(userRepository.existsByEmail("vikash@test.com")).thenReturn(true);
            doNothing().when(emailService).sendOtpEmail(anyString(), anyString(), any());

            ApiResponseDTO<String> resp = authService.forgotPassword("vikash@test.com");

            assertTrue(resp.isSuccess());
            verify(otpRepository).save(any(OtpVerification.class));
            verify(emailService).sendOtpEmail(anyString(), anyString(), eq(OtpType.PASSWORD_RESET));
        }

        @Test @DisplayName("forgotPassword — silently succeeds for unknown email (security)")
        void forgotPassword_unknownEmail() {
            when(userRepository.existsByEmail("unknown@x.com")).thenReturn(false);
            ApiResponseDTO<String> resp = authService.forgotPassword("unknown@x.com");
            assertTrue(resp.isSuccess());
            verify(emailService, never()).sendOtpEmail(anyString(), anyString(), any());
        }

        @Test @DisplayName("resetPassword — success when OTP verified flag is set")
        void resetPassword_success() {
            regularUser.setIsPasswordResetVerified(true);
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            when(passwordEncoder.encode("NewPass@1")).thenReturn("newHash");

            ApiResponseDTO<String> resp = authService.resetPassword("vikash@test.com", "NewPass@1");

            assertTrue(resp.isSuccess());
            assertFalse(regularUser.getIsPasswordResetVerified()); // flag cleared
        }

        @Test @DisplayName("resetPassword — fail: OTP not verified first")
        void resetPassword_notVerified() {
            regularUser.setIsPasswordResetVerified(false);
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            assertThrows(InvalidOtpException.class,
                    () -> authService.resetPassword("vikash@test.com", "NewPass@1"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. PROFILE
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Profile")
    class ProfileTests {

        @Test @DisplayName("getUserById — found")
        void getUserById_success() {
            when(userRepository.findById(1)).thenReturn(Optional.of(regularUser));
            ApiResponseDTO<User> resp = authService.getUserById(1);
            assertTrue(resp.isSuccess());
            assertEquals("vikash", resp.getData().getUsername());
        }

        @Test @DisplayName("getUserById — not found")
        void getUserById_notFound() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());
            assertThrows(UserNotFoundException.class, () -> authService.getUserById(99));
        }

        @Test @DisplayName("updateProfile — success")
        void updateProfile_success() {
            UpdateProfileRequestDTO req = UpdateProfileRequestDTO.builder()
                    .username("vikash_new").fullName("Updated").bio("Dev").build();
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            when(userRepository.existsByUsername("vikash_new")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(regularUser);

            ApiResponseDTO<User> resp = authService.updateProfileByEmail("vikash@test.com", req);

            assertTrue(resp.isSuccess());
            verify(userRepository).save(argThat(u ->
                    "vikash_new".equals(u.getUsername()) && "Dev".equals(u.getBio())
            ));
        }

        @Test @DisplayName("updateProfile — fail: username already taken")
        void updateProfile_usernameTaken() {
            UpdateProfileRequestDTO req = UpdateProfileRequestDTO.builder()
                    .username("taken").build();
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            when(userRepository.existsByUsername("taken")).thenReturn(true);
            assertThrows(UserAlreadyExistsException.class,
                    () -> authService.updateProfileByEmail("vikash@test.com", req));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. TOKEN
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Token Management")
    class TokenTests {

        @Test @DisplayName("logout — token blacklisted successfully")
        void logout_success() {
            when(blacklistedTokenRepository.existsByToken("tok")).thenReturn(false);
            when(jwtTokenProvider.extractExpiration("tok"))
                    .thenReturn(new Date(System.currentTimeMillis() + 3600000));

            ApiResponseDTO<String> resp = authService.logout("tok");

            assertTrue(resp.isSuccess());
            verify(blacklistedTokenRepository).save(any());
        }

        @Test @DisplayName("logout — already blacklisted: silent success")
        void logout_alreadyBlacklisted() {
            when(blacklistedTokenRepository.existsByToken("old")).thenReturn(true);
            ApiResponseDTO<String> resp = authService.logout("old");
            assertTrue(resp.isSuccess());
            verify(blacklistedTokenRepository, never()).save(any());
        }

        @Test @DisplayName("validateToken — valid and not blacklisted returns true")
        void validateToken_valid() {
            when(jwtTokenProvider.validateToken("good")).thenReturn(true);
            when(blacklistedTokenRepository.existsByToken("good")).thenReturn(false);
            assertTrue(authService.validateToken("good"));
        }

        @Test @DisplayName("validateToken — blacklisted token returns false")
        void validateToken_blacklisted() {
            when(jwtTokenProvider.validateToken("bl")).thenReturn(true);
            when(blacklistedTokenRepository.existsByToken("bl")).thenReturn(true);
            assertFalse(authService.validateToken("bl"));
        }

        @Test @DisplayName("refreshToken — issues new access token")
        void refreshToken_success() {
            when(jwtTokenProvider.validateToken("ref")).thenReturn(true);
            when(jwtTokenProvider.getEmailFromToken("ref")).thenReturn("vikash@test.com");
            when(userRepository.findByEmail("vikash@test.com")).thenReturn(Optional.of(regularUser));
            when(jwtTokenProvider.generateAccessToken(regularUser)).thenReturn("new_access");
            when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(86400000L);

            ApiResponseDTO<LoginResponseDTO> resp = authService.refreshToken("ref");

            assertTrue(resp.isSuccess());
            assertEquals("new_access", resp.getData().getAccessToken());
        }

        @Test @DisplayName("refreshToken — expired token throws InvalidCredentialsException")
        void refreshToken_expired() {
            when(jwtTokenProvider.validateToken("exp")).thenReturn(false);
            assertThrows(InvalidCredentialsException.class, () -> authService.refreshToken("exp"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 7. ADMIN
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Admin Operations")
    class AdminTests {

        @Test @DisplayName("getAllUsers — returns full user list")
        void getAllUsers_success() {
            when(userRepository.findAll())
                    .thenReturn(List.of(regularUser, adminUser, moderatorUser));
            ApiResponseDTO<List<User>> resp = authService.getAllUsers();
            assertEquals(3, resp.getData().size());
        }

        @Test @DisplayName("getUsersByRole(MODERATOR) — returns only moderators")
        void getUsersByRole_moderator() {
            when(userRepository.findAllByRole(Role.MODERATOR)).thenReturn(List.of(moderatorUser));
            ApiResponseDTO<List<User>> resp = authService.getUsersByRole("MODERATOR");
            assertEquals(1, resp.getData().size());
            assertEquals(Role.MODERATOR, resp.getData().get(0).getRole());
        }

        @Test @DisplayName("getUsersByRole(ADMIN) — returns only admins")
        void getUsersByRole_admin() {
            when(userRepository.findAllByRole(Role.ADMIN)).thenReturn(List.of(adminUser));
            ApiResponseDTO<List<User>> resp = authService.getUsersByRole("ADMIN");
            assertEquals(Role.ADMIN, resp.getData().get(0).getRole());
        }

        @Test @DisplayName("getUsersByRole — invalid role string throws IllegalArgumentException")
        void getUsersByRole_invalid() {
            assertThrows(IllegalArgumentException.class,
                    () -> authService.getUsersByRole("SUPERADMIN"));
        }

        @Test @DisplayName("deactivateUser — suspends user successfully")
        void deactivateUser_success() {
            when(userRepository.existsById(1)).thenReturn(true);
            ApiResponseDTO<String> resp = authService.deactivateUser(1);
            assertTrue(resp.isSuccess());
            verify(userRepository).deactivateByUserId(1);
        }

        @Test @DisplayName("deactivateUser — user not found throws UserNotFoundException")
        void deactivateUser_notFound() {
            when(userRepository.existsById(99)).thenReturn(false);
            assertThrows(UserNotFoundException.class, () -> authService.deactivateUser(99));
        }

        @Test @DisplayName("reactivateUser — restores suspended user")
        void reactivateUser_success() {
            when(userRepository.findById(4)).thenReturn(Optional.of(suspendedUser));
            ApiResponseDTO<String> resp = authService.reactivateUser(4);
            assertTrue(resp.isSuccess());
            assertTrue(suspendedUser.getIsActive());
            verify(userRepository).save(suspendedUser);
        }

        @Test @DisplayName("reactivateUser — user not found throws UserNotFoundException")
        void reactivateUser_notFound() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());
            assertThrows(UserNotFoundException.class, () -> authService.reactivateUser(99));
        }

        // ── deleteUser ────────────────────────────────────────────────

        @Test @DisplayName("deleteUser — admin permanently deletes another user")
        void deleteUser_success() {
            when(userRepository.existsById(1)).thenReturn(true);
            ApiResponseDTO<String> resp = authService.deleteUser(2, 1);
            assertTrue(resp.isSuccess());
            verify(userRepository).deleteByUserId(1);
        }

        @Test @DisplayName("deleteUser — admin cannot delete own account (self-protection)")
        void deleteUser_selfDelete() {
            assertThrows(UnauthorizedAccessException.class,
                    () -> authService.deleteUser(2, 2));
            verify(userRepository, never()).deleteByUserId(anyInt());
        }

        @Test @DisplayName("deleteUser — target not found throws UserNotFoundException")
        void deleteUser_targetNotFound() {
            when(userRepository.existsById(99)).thenReturn(false);
            assertThrows(UserNotFoundException.class, () -> authService.deleteUser(2, 99));
        }

        // ── assignRole ────────────────────────────────────────────────

        @Test @DisplayName("assignRole — admin promotes USER to MODERATOR")
        void assignRole_toModerator() {
            when(userRepository.findById(1)).thenReturn(Optional.of(regularUser));
            ApiResponseDTO<String> resp = authService.assignRole(2, 1, "MODERATOR");
            assertTrue(resp.isSuccess());
            assertTrue(resp.getMessage().contains("MODERATOR"));
            verify(userRepository).updateRoleByUserId(1, Role.MODERATOR);
        }

        @Test @DisplayName("assignRole — admin promotes USER to ADMIN")
        void assignRole_toAdmin() {
            when(userRepository.findById(1)).thenReturn(Optional.of(regularUser));
            ApiResponseDTO<String> resp = authService.assignRole(2, 1, "ADMIN");
            assertTrue(resp.isSuccess());
            verify(userRepository).updateRoleByUserId(1, Role.ADMIN);
        }

        @Test @DisplayName("assignRole — admin demotes MODERATOR back to USER")
        void assignRole_toUser() {
            when(userRepository.findById(3)).thenReturn(Optional.of(moderatorUser));
            ApiResponseDTO<String> resp = authService.assignRole(2, 3, "USER");
            assertTrue(resp.isSuccess());
            verify(userRepository).updateRoleByUserId(3, Role.USER);
        }

        @Test @DisplayName("assignRole — admin cannot change own role (self-protection)")
        void assignRole_selfChange() {
            assertThrows(UnauthorizedAccessException.class,
                    () -> authService.assignRole(2, 2, "USER"));
            verify(userRepository, never()).updateRoleByUserId(anyInt(), any());
        }

        @Test @DisplayName("assignRole — invalid role string throws IllegalArgumentException")
        void assignRole_invalidRole() {
            when(userRepository.findById(1)).thenReturn(Optional.of(regularUser));
            assertThrows(IllegalArgumentException.class,
                    () -> authService.assignRole(2, 1, "SUPERADMIN"));
        }

        @Test @DisplayName("assignRole — target not found throws UserNotFoundException")
        void assignRole_targetNotFound() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());
            assertThrows(UserNotFoundException.class,
                    () -> authService.assignRole(2, 99, "MODERATOR"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 8. MODERATOR
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Moderator Operations")
    class ModeratorTests {

        @Test @DisplayName("getSuspendedUsers — returns all inactive accounts")
        void getSuspendedUsers_success() {
            when(userRepository.findByIsActive(false)).thenReturn(List.of(suspendedUser));
            ApiResponseDTO<List<User>> resp = authService.getSuspendedUsers();
            assertTrue(resp.isSuccess());
            assertEquals(1, resp.getData().size());
            assertFalse(resp.getData().get(0).getIsActive());
        }

        @Test @DisplayName("getSuspendedUsers — empty list when no suspensions")
        void getSuspendedUsers_empty() {
            when(userRepository.findByIsActive(false)).thenReturn(List.of());
            ApiResponseDTO<List<User>> resp = authService.getSuspendedUsers();
            assertTrue(resp.isSuccess());
            assertTrue(resp.getData().isEmpty());
        }

        @Test @DisplayName("getUserById — moderator can fetch any user for review")
        void moderatorGetUser() {
            when(userRepository.findById(1)).thenReturn(Optional.of(regularUser));
            ApiResponseDTO<User> resp = authService.getUserById(1);
            assertTrue(resp.isSuccess());
            assertEquals("vikash", resp.getData().getUsername());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 9. SEARCH
    // ═══════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Search")
    class SearchTests {

        @Test @DisplayName("searchUsers — returns matches")
        void searchUsers_match() {
            when(userRepository.searchByUsername("vikash"))
                    .thenReturn(List.of(regularUser, adminUser));
            ApiResponseDTO<List<User>> resp = authService.searchUsers("vikash");
            assertTrue(resp.isSuccess());
            assertEquals(2, resp.getData().size());
        }

        @Test @DisplayName("searchUsers — empty list for no match")
        void searchUsers_noMatch() {
            when(userRepository.searchByUsername("nobody")).thenReturn(List.of());
            ApiResponseDTO<List<User>> resp = authService.searchUsers("nobody");
            assertTrue(resp.isSuccess());
            assertTrue(resp.getData().isEmpty());
        }
    }
}