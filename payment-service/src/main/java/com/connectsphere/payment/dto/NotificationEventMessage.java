package com.connectsphere.payment.dto;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEventMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer recipientId;
    private Integer actorId;
    private String type; // e.g. PAYMENT_SUCCESS
    private String message;
    private Integer targetId;
    private String targetType;
    private String deepLinkUrl;
}
