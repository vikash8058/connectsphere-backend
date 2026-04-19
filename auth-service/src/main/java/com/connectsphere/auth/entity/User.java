package com.connectsphere.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * User Entity - Maps to 'users' table in connectsphere_auth database
 *
 * Stores all user account information as per the ConnectSphere case study:
 *   userId, username, email, passwordHash, fullName, bio,
 *   profilePicUrl, role, provider, isActive, createdAt
 *
 * provider field supports LOCAL (email/password), GITHUB, GOOGLE OAuth2.
 * isActive is used for soft-delete / account suspension by Admin.
 * isEmailVerified gates login for LOCAL accounts.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "passwordHash")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    /**
     * Unique username chosen by the user - used for @mentions and profile URL
     */
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /**
     * Display name shown on posts and profile
     */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /**
     * Email - unique identifier, used for LOCAL login and notifications
     */
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Bcrypt hashed password - null for OAuth accounts
     */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Short bio displayed on the user profile page
     */
    @Column(name = "bio", length = 300)
    private String bio;

    /**
     * CDN URL of the user's profile picture
     */
    @Column(name = "profile_pic_url", length = 500)
    private String profilePicUrl;

    /**
     * Role: USER, ADMIN, or MODERATOR
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /**
     * How the user registered: LOCAL, GITHUB, or GOOGLE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    /**
     * Soft-delete / suspension flag - false means account is suspended
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Email verification status - LOCAL accounts must verify before login
     * OAuth accounts are auto-verified
     */
    @Column(name = "is_email_verified", nullable = false)
    @Builder.Default
    private Boolean isEmailVerified = false;
    
    @Column(name = "is_password_reset_verified", nullable = false)
    @Builder.Default
    private Boolean isPasswordResetVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}