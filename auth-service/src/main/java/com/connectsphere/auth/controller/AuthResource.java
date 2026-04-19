package com.connectsphere.auth.controller;

import com.connectsphere.auth.dto.*;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AuthResource - REST Controller for ConnectSphere Auth Service
 *
 * Base path: /api/v1/auth  (context-path defined in application.yml)
 *
 * ── PUBLIC (no token required) ───────────────────────────────────────────────
 *   POST /auth/register              Register with email+password
 *   POST /auth/verify-otp            Verify email OTP
 *   POST /auth/resend-otp            Resend OTP
 *   POST /auth/login                 Login -> JWT tokens
 *   POST /auth/refresh               Refresh access token
 *   POST /auth/forgot-password       Start password reset flow
 *   POST /auth/reset-password        Complete password reset
 *
 * ── PROTECTED (valid JWT required) ───────────────────────────────────────────
 *   POST /auth/logout                Blacklist token
 *   GET  /auth/validate              Validate JWT (called by API Gateway)
 *   GET  /auth/profile               Get own profile
 *   PUT  /auth/profile               Update own profile
 *   PUT  /auth/password              Change own password
 *   GET  /auth/search?query=         Search users by username or full name
 *   GET  /auth/users/{userId}        Get any user by ID
 *
 * ── ADMIN ONLY (/auth/admin/**) ──────────────────────────────────────────────
 *   GET    /auth/admin/users                        Get all users
 *   GET    /auth/admin/users/role/{role}             Get users by role
 *   GET    /auth/admin/users/{userId}                Get user by ID (admin view)
 *   PUT    /auth/admin/users/{userId}/deactivate     Suspend user account
 *   PUT    /auth/admin/users/{userId}/reactivate     Reactivate user account
 *   DELETE /auth/admin/users/{userId}                Permanently delete user
 *   PUT    /auth/admin/users/{userId}/role           Assign/change user role
 *
 * ── MODERATOR + ADMIN (/auth/moderator/**) ───────────────────────────────────
 *   GET  /auth/moderator/users/suspended     Get all suspended accounts
 *   GET  /auth/moderator/users/{userId}      Get any user by ID for moderation review
 */
@RestController
@RequestMapping("/auth")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Authentication",
     description = "Registration, login, OTP, JWT management, user search, admin & moderator management")
public class AuthResource {

    private final AuthService authService;

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/register")
    @Operation(summary = "Register new user",
               description = "LOCAL registration — sends OTP to email for verification")
    public ResponseEntity<ApiResponseDTO<String>> register(
            @Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP",
               description = "Verifies email OTP for account activation or password reset")
    public ResponseEntity<ApiResponseDTO<String>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequestDTO request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP")
    public ResponseEntity<ApiResponseDTO<String>> resendOtp(
            @RequestParam String email,
            @RequestParam String otpType) {
        return ResponseEntity.ok(authService.resendOtp(email, otpType));
    }

    @PostMapping("/login")
    @Operation(summary = "User login",
               description = "Authenticates with email+password — returns JWT access + refresh tokens")
    public ResponseEntity<ApiResponseDTO<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponseDTO<LoginResponseDTO>> refreshToken(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refreshToken(body.get("refreshToken")));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password — sends OTP to registered email")
    public ResponseEntity<ApiResponseDTO<String>> forgotPassword(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.forgotPassword(body.get("email")));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password after OTP verification")
    public ResponseEntity<ApiResponseDTO<String>> resetPassword(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                authService.resetPassword(body.get("email"), body.get("newPassword")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PROTECTED ENDPOINTS (any authenticated user)
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/logout")
    @Operation(summary = "Logout — blacklists the current JWT token")
    public ResponseEntity<ApiResponseDTO<String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid Authorization header");
        }
        return ResponseEntity.ok(authService.logout(authHeader.substring(7)));
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token — called by API Gateway")
    public ResponseEntity<Boolean> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.validateToken(authHeader.replace("Bearer ", "")));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get own profile")
    public ResponseEntity<ApiResponseDTO<User>> getProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(authService.getUserByEmail(auth.getName()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update own profile — username, fullName, bio, profilePicUrl")
    public ResponseEntity<ApiResponseDTO<User>> updateProfile(
            @RequestBody UpdateProfileRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(authService.updateProfileByEmail(auth.getName(), request));
    }

    @PutMapping("/password")
    @Operation(summary = "Change own password")
    public ResponseEntity<ApiResponseDTO<String>> changePassword(
            @RequestBody ChangePasswordRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(authService.changePassword(auth.getName(), request));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by username or full name",
               description = "Used for user discovery, @mention autocomplete, and guest search")
    public ResponseEntity<ApiResponseDTO<List<User>>> searchUsers(
            @RequestParam String query) {
        return ResponseEntity.ok(authService.searchUsers(query));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get any user's public profile by ID")
    public ResponseEntity<ApiResponseDTO<User>> getUserById(@PathVariable Integer userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN ONLY  (/auth/admin/**)
    //
    // TWO layers of protection:
    //   1. URL-level:    SecurityConfig  → .requestMatchers("/auth/admin/**").hasRole("ADMIN")
    //   2. Method-level: @PreAuthorize("hasRole('ADMIN')")  — defense in depth
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users — Admin only",
               description = "Returns all registered users across all roles")
    public ResponseEntity<ApiResponseDTO<List<User>>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @GetMapping("/admin/users/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get users by role — Admin only",
               description = "Filter users by USER, ADMIN, or MODERATOR")
    public ResponseEntity<ApiResponseDTO<List<User>>> getUsersByRole(
            @PathVariable String role) {
        return ResponseEntity.ok(authService.getUsersByRole(role));
    }

    @GetMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID — Admin only",
               description = "Fetch full user details for admin review")
    public ResponseEntity<ApiResponseDTO<User>> getAdminUserById(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    @PutMapping("/admin/users/{userId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suspend user account — Admin only",
               description = "Soft-suspends a user. User cannot log in while suspended.")
    public ResponseEntity<ApiResponseDTO<String>> deactivateUser(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(authService.deactivateUser(userId));
    }

    @PutMapping("/admin/users/{userId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivate suspended account — Admin only",
               description = "Restores login access for a previously suspended user")
    public ResponseEntity<ApiResponseDTO<String>> reactivateUser(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(authService.reactivateUser(userId));
    }

    @DeleteMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Permanently delete user — Admin only",
               description = "Hard deletes a user account. Admin cannot delete their own account.")
    public ResponseEntity<ApiResponseDTO<String>> deleteUser(
            @PathVariable Integer userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = authService.getUserByEmail(auth.getName()).getData();
        return ResponseEntity.ok(authService.deleteUser(admin.getUserId(), userId));
    }

    @PutMapping("/admin/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign role to user — Admin only",
               description = "Promotes or demotes a user to USER, MODERATOR, or ADMIN. Cannot change own role.")
    public ResponseEntity<ApiResponseDTO<String>> assignRole(
            @PathVariable Integer userId,
            @Valid @RequestBody AssignRoleRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = authService.getUserByEmail(auth.getName()).getData();
        return ResponseEntity.ok(authService.assignRole(admin.getUserId(), userId, request.getRole()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MODERATOR + ADMIN  (/auth/moderator/**)
    //
    // TWO layers of protection:
    //   1. URL-level:    SecurityConfig  → .requestMatchers("/auth/moderator/**").hasAnyRole("ADMIN","MODERATOR")
    //   2. Method-level: @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")  — defense in depth
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/moderator/users/suspended")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get all suspended users — Admin & Moderator",
               description = "Returns all accounts suspended via deactivate. "
                           + "Used during content/account moderation review.")
    public ResponseEntity<ApiResponseDTO<List<User>>> getSuspendedUsers() {
        return ResponseEntity.ok(authService.getSuspendedUsers());
    }

    @GetMapping("/moderator/users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get user by ID for moderation review — Admin & Moderator",
               description = "Fetches a user's full profile for moderation purposes. "
                           + "Allows moderators to review reporter and reported accounts.")
    public ResponseEntity<ApiResponseDTO<User>> getModeratorUserById(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }
}