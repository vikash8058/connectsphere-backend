package com.connectsphere.payment.controller;

import com.connectsphere.payment.dto.*;
import com.connectsphere.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentResource {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponseDTO<OrderResponseDTO>> createOrder(
            @RequestBody OrderRequestDTO request,
            @RequestHeader("X-User-Id") String userIdHeader) {
        Integer userId = Integer.parseInt(userIdHeader);
        return ResponseEntity.ok(paymentService.createOrder(userId, request));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponseDTO<String>> verifyPayment(
            @RequestBody PaymentVerificationDTO request,
            @RequestHeader("X-User-Id") String userIdHeader) {
        Integer userId = Integer.parseInt(userIdHeader);
        return ResponseEntity.ok(paymentService.verifyPayment(userId, request));
    }

    @GetMapping("/subscription-status")
    public ResponseEntity<ApiResponseDTO<SubscriptionStatusDTO>> getSubscriptionStatus(
            @RequestHeader("X-User-Id") String userIdHeader) {
        Integer userId = Integer.parseInt(userIdHeader);
        return ResponseEntity.ok(paymentService.getSubscriptionStatus(userId));
    }
}
