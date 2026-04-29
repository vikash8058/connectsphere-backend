package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationRequestDTO {

    @NotNull(message = "recipientId is required")
    private Integer recipientId;

    @NotNull(message = "actorId is required")
    private Integer actorId;

    @NotNull(message = "type is required")
    private NotificationType type;

    @NotNull(message = "message is required")
    private String message;

    /** Nullable — not set for FOLLOW type */
    private Integer targetId;

    /** "POST" or "COMMENT" — nullable for FOLLOW type */
    private String targetType;

    /** Frontend deep-link URL — e.g. "/posts/42", "/profile/7" */
    private String deepLinkUrl;
}