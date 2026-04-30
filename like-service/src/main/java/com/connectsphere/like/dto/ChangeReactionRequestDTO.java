package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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