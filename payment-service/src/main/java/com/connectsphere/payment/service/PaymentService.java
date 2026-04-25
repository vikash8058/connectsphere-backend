package com.connectsphere.payment.service;

import com.connectsphere.payment.dto.*;

public interface PaymentService {
    ApiResponseDTO<OrderResponseDTO> createOrder(Integer userId, OrderRequestDTO request);
    ApiResponseDTO<String> verifyPayment(Integer userId, PaymentVerificationDTO request);
    ApiResponseDTO<SubscriptionStatusDTO> getSubscriptionStatus(Integer userId);
}
