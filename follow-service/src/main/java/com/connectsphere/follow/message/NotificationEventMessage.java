package com.connectsphere.follow.message;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEventMessage implements Serializable {
    private Integer recipientId;
    private Integer actorId;
    private String type;       // "LIKE", "COMMENT" etc as String (no enum dependency)
    private String message;
    private Integer targetId;
    private String targetType;
    private String deepLinkUrl;
}