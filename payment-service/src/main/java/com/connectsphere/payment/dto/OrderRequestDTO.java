package com.connectsphere.payment.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderRequestDTO {
    private String planType; // ELITE_MONTHLY, ELITE_YEARLY
}
