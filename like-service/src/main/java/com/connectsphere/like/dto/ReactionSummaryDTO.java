package com.connectsphere.like.dto;

import lombok.*;

import java.util.Map;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionSummaryDTO {

    private Integer targetId;
    private String targetType;
    private Integer totalCount;

    // Map of reactionType → count
    private Map<String, Integer> reactions;
}