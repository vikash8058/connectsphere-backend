package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * ChangeReactionRequestDTO - Payload for PUT /likes/change (changeReaction)
 *
 * Case study section 2.3:
 * "Like or react to posts (Like, Love, Haha, Wow, Sad, Angry); change or remove reactions."
 *
 * Flow: delete old reaction → insert new reaction (atomic @Transactional).
 * Also re-syncs post/comment counter (no net change in count, only emoji changes).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeReactionRequestDTO {

    @NotNull(message = "targetId is required")
    private Integer targetId;

    @NotNull(message = "targetType is required — POST or COMMENT")
    private TargetType targetType;

    @NotNull(message = "newReactionType is required")
    private ReactionType newReactionType;
}