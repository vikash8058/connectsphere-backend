package com.connectsphere.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.connectsphere.auth.entity.BlacklistedToken;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    boolean existsByToken(String token);
}