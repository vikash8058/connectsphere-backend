package com.connectsphere.payment.repository;

import com.connectsphere.payment.entity.EliteSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EliteSubscriptionRepository extends JpaRepository<EliteSubscription, Long> {
    Optional<EliteSubscription> findByUserId(Integer userId);
}
