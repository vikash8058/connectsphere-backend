package com.connectsphere.auth.repository;

import com.connectsphere.auth.entity.OtpType;
import com.connectsphere.auth.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * OtpVerificationRepository - Data Access Layer for OTP Entity
 */
@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Integer> {

    @Query("""
            SELECT o FROM OtpVerification o
            WHERE o.email = :email
            AND o.otpType = :otpType
            AND o.isUsed = false
            AND o.expiresAt > CURRENT_TIMESTAMP
            ORDER BY o.createdAt DESC
            """)
    java.util.List<OtpVerification> findValidOtps(
            @Param("email") String email,
            @Param("otpType") OtpType otpType
    );

    @Modifying
    @Query("UPDATE OtpVerification o SET o.isUsed = true WHERE o.otpId = :otpId")
    void markAsUsed(@Param("otpId") Integer otpId);

    void deleteAllByEmailAndOtpType(String email, OtpType otpType);
}