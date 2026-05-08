package com.connectsphere.payment.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderRequestDTO {
    private String planType; // ELITE_MONTHLY, ELITE_YEARLY
}
