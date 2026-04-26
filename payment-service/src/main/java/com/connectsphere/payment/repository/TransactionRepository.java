package com.connectsphere.payment.repository;

import com.connectsphere.payment.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByRazorpayOrderId(String razorpayOrderId);
}
