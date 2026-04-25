package com.connectsphere.payment.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubscriptionStatusDTO {
    private Boolean isElite;
    private LocalDateTime eliteUntil;
    private String planType;
}
