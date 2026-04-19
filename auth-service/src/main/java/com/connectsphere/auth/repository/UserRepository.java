package com.connectsphere.auth.repository;

import com.connectsphere.auth.entity.Role;
import com.connectsphere.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserRepository - Data Access Layer for User Entity
 *
 * Custom queries as per ConnectSphere spec:
 *   findByEmail, findByUsername, existsByEmail, existsByUsername,
 *   findAllByRole, searchByUsername, deleteByUserId
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findAllByRole(Role role);

    List<User> findByIsActive(Boolean isActive);

    /**
     * Case-insensitive username search for the user discovery / search feature
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByUsername(@Param("query") String query);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.userId = :userId")
    void updateLastLoginAt(@Param("userId") Integer userId,
                           @Param("loginTime") LocalDateTime loginTime);

    @Modifying
    @Query("UPDATE User u SET u.isActive = false WHERE u.userId = :userId")
    void deactivateByUserId(@Param("userId") Integer userId);

    @Modifying
    @Query("UPDATE User u SET u.isEmailVerified = true WHERE u.email = :email")
    void markEmailVerified(@Param("email") String email);

    /**
     * Permanently delete a user by ID — ADMIN only.
     * Hard delete (removes row from DB).
     */
    @Modifying
    @Query("DELETE FROM User u WHERE u.userId = :userId")
    void deleteByUserId(@Param("userId") Integer userId);

    /**
     * Update a user's role — used by Admin to assign MODERATOR or change roles.
     */
    @Modifying
    @Query("UPDATE User u SET u.role = :role WHERE u.userId = :userId")
    void updateRoleByUserId(@Param("userId") Integer userId, @Param("role") Role role);
}