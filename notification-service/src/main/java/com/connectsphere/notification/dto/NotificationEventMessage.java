package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
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
    private NotificationType type;
    private String message;
    private Integer targetId;
    private String targetType;
    private String deepLinkUrl;
}