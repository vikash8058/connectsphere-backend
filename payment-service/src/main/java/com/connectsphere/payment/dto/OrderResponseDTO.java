package com.connectsphere.payment.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderResponseDTO {
    private String orderId;
    private Integer amount;
    private String currency;
    private String keyId; // Razorpay Key ID for frontend
}
