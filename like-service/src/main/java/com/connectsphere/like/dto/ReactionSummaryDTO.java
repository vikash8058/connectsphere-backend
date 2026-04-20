package com.connectsphere.like.dto;

import lombok.*;

import java.util.Map;

/**
 * ReactionSummaryDTO - Aggregated reaction counts by emoji type.
 *
 * Case study section 4.4:
 * "exposes a reaction summary map that aggregates counts by type
 *  for efficient rendering of emoji reaction bars."
 *
 * Glossary: "Reaction Summary — An aggregated map of reaction types to counts
 *            displayed as an emoji bar on posts"
 *
 * Example response:
 * {
 *   "targetId": 42,
 *   "targetType": "POST",
 *   "totalCount": 17,
 *   "reactions": {
 *     "LIKE":  10,
 *     "LOVE":  5,
 *     "HAHA":  2,
 *     "WOW":   0,
 *     "SAD":   0,
 *     "ANGRY": 0
 *   }
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionSummaryDTO {

    private Integer targetId;
    private String targetType;
    private Integer totalCount;

    /** Map of reactionType → count */
    private Map<String, Integer> reactions;
}