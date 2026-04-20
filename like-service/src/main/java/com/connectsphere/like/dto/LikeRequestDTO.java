package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * LikeRequestDTO - Payload for POST /likes (likeTarget)
 *
 * Case study section 4.4:
 *   targetId     - ID of the post or comment
 *   targetType   - POST or COMMENT
 *   reactionType - LIKE (default) / LOVE / HAHA / WOW / SAD / ANGRY
 *
 * userId is extracted from JWT — never from request body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LikeRequestDTO {

    @NotNull(message = "targetId is required")
    private Integer targetId;

    @NotNull(message = "targetType is required — POST or COMMENT")
    private TargetType targetType;

    /** Defaults to LIKE if not specified */
    @Builder.Default
    private ReactionType reactionType = ReactionType.LIKE;
}