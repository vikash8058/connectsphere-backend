package com.connectsphere.payment.service;

import com.connectsphere.payment.client.AuthClient;
import com.connectsphere.payment.dto.*;
import com.connectsphere.payment.entity.EliteSubscription;
import com.connectsphere.payment.entity.Transaction;
import com.connectsphere.payment.messaging.NotificationPublisher;
import com.connectsphere.payment.repository.EliteSubscriptionRepository;
import com.connectsphere.payment.repository.TransactionRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final TransactionRepository transactionRepository;
    private final EliteSubscriptionRepository subscriptionRepository;
    private final AuthClient authClient;
    private final NotificationPublisher notificationPublisher;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    @Override
    @Transactional
    public ApiResponseDTO<OrderResponseDTO> createOrder(Integer userId, OrderRequestDTO request) {
        log.info("Creating order for user: {} plan: {}", userId, request.getPlanType());
        try {
            if (request.getPlanType() == null) throw new IllegalArgumentException("Plan type is required");
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            int amount = request.getPlanType().equals("ELITE_MONTHLY") ? 19900 : 199900; // in paise
            
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            Order order = razorpay.orders.create(orderRequest);

            Transaction transaction = Transaction.builder()
                    .userId(userId)
                    .razorpayOrderId(order.get("id"))
                    .amount(BigDecimal.valueOf(amount / 100.0))
                    .currency("INR")
                    .status("PENDING")
                    .planType(request.getPlanType())
                    .build();

            transactionRepository.save(transaction);

            OrderResponseDTO response = OrderResponseDTO.builder()
                    .orderId(order.get("id"))
                    .amount(amount)
                    .currency("INR")
                    .keyId(razorpayKeyId)
                    .build();

            return ApiResponseDTO.success("Order created", response);

        } catch (Exception e) {
            log.error("Order creation failed for user {}: {}", userId, e.getMessage(), e);
            return ApiResponseDTO.error("Failed to create Razorpay order: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> verifyPayment(Integer userId, PaymentVerificationDTO request) {
        try {
            // Verify signature
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.getRazorpayOrderId());
            options.put("razorpay_payment_id", request.getRazorpayPaymentId());
            options.put("razorpay_signature", request.getRazorpaySignature());

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);

            if (!isValid) {
                return ApiResponseDTO.error("Invalid payment signature");
            }

            // Update transaction
            Transaction transaction = transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                    .orElseThrow(() -> new RuntimeException("Transaction not found"));

            transaction.setRazorpayPaymentId(request.getRazorpayPaymentId());
            transaction.setRazorpaySignature(request.getRazorpaySignature());
            transaction.setStatus("SUCCESS");
            transactionRepository.save(transaction);

            // Update subscription
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiry = transaction.getPlanType().equals("ELITE_MONTHLY") 
                    ? now.plusMonths(1) : now.plusYears(1);

            Optional<EliteSubscription> subOpt = subscriptionRepository.findByUserId(userId);
            EliteSubscription sub = subOpt.orElse(new EliteSubscription());
            sub.setUserId(userId);
            sub.setStartDate(now);
            sub.setEndDate(expiry);
            sub.setIsActive(true);
            sub.setPlanType(transaction.getPlanType());
            subscriptionRepository.save(sub);

            // Sync with auth-service
            authClient.updateEliteStatus(userId, true, expiry.toString());

            // Send notification via RabbitMQ
            notificationPublisher.sendNotification(NotificationEventMessage.builder()
                    .recipientId(userId)
                    .actorId(0) // System
                    .type("PAYMENT_SUCCESS")
                    .message("Welcome to ConnectSphere Elite! Your badge is active until " + expiry.toLocalDate())
                    .build());

            return ApiResponseDTO.success("Payment verified and subscription activated", expiry.toString());

        } catch (Exception e) {
            log.error("Payment verification failed: {}", e.getMessage());
            return ApiResponseDTO.error("Verification failed: " + e.getMessage());
        }
    }

    @Override
    public ApiResponseDTO<SubscriptionStatusDTO> getSubscriptionStatus(Integer userId) {
        Optional<EliteSubscription> sub = subscriptionRepository.findByUserId(userId);
        boolean isActive = sub.isPresent() && sub.get().isCurrentlyActive();
        
        return ApiResponseDTO.success("Status fetched", SubscriptionStatusDTO.builder()
                .isElite(isActive)
                .eliteUntil(sub.map(EliteSubscription::getEndDate).orElse(null))
                .planType(sub.map(EliteSubscription::getPlanType).orElse(null))
                .build());
    }
}
