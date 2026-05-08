package com.connectsphere.payment.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentVerificationDTO {
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
}
