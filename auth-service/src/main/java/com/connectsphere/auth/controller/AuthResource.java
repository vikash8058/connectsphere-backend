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

@RestController
@RequestMapping("/auth")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthResource {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponseDTO<String>> register(@Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponseDTO<String>> verifyOtp(@Valid @RequestBody OtpVerifyRequestDTO request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponseDTO<String>> resendOtp(@RequestParam String email, @RequestParam String otpType) {
        return ResponseEntity.ok(authService.resendOtp(email, otpType));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDTO<LoginResponseDTO>> refreshToken(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refreshToken(body.get("refreshToken")));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponseDTO<String>> forgotPassword(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.forgotPassword(body.get("email")));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponseDTO<String>> resetPassword(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.resetPassword(body.get("email"), body.get("newPassword")));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDTO<String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return ResponseEntity.ok(authService.logout(authHeader.substring(7)));
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateToken(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.validateToken(authHeader.replace("Bearer ", "")));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponseDTO<User>> getProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(authService.getUserByEmail(auth.getName()));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponseDTO<User>> updateProfile(@Valid @RequestBody UpdateProfileRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(authService.updateProfileByEmail(auth.getName(), request));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponseDTO<String>> changePassword(@Valid @RequestBody ChangePasswordRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(authService.changePassword(auth.getName(), request));
    }

    @PostMapping("/set-password")
    public ResponseEntity<ApiResponseDTO<String>> setPassword(@Valid @RequestBody SetPasswordRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(authService.setInitialPassword(auth.getName(), request));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponseDTO<List<User>>> searchUsers(@RequestParam String query) {
        return ResponseEntity.ok(authService.searchUsers(query));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponseDTO<User>> getUserById(@PathVariable Integer userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    // Admin & Moderator

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<List<User>>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @PutMapping("/admin/users/{userId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<String>> deactivateUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(authService.deactivateUser(userId));
    }

    @PutMapping("/admin/users/{userId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<String>> reactivateUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(authService.reactivateUser(userId));
    }

    @DeleteMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<String>> deleteUser(@PathVariable Integer userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = authService.getUserByEmail(auth.getName()).getData();
        return ResponseEntity.ok(authService.deleteUser(admin.getUserId(), userId));
    }

    @GetMapping("/moderator/users/suspended")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<ApiResponseDTO<List<User>>> getSuspendedUsers() {
        return ResponseEntity.ok(authService.getSuspendedUsers());
    }

    // Internal

    @PutMapping("/internal/update-elite-status")
    public ResponseEntity<ApiResponseDTO<String>> updateEliteStatus(
            @RequestParam Integer userId,
            @RequestParam Boolean isElite,
            @RequestParam(required = false) String eliteUntil) {
        return ResponseEntity.ok(authService.updateEliteStatus(userId, isElite, eliteUntil));
    }
}