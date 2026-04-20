package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * LikeResponseDTO - Safe like data returned in API responses
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LikeResponseDTO {

    private Integer likeId;
    private Integer userId;
    private Integer targetId;
    private TargetType targetType;
    private ReactionType reactionType;
    private LocalDateTime createdAt;
}