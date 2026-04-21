package com.connectsphere.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

/**
 * HashtagResponseDTO - Response DTO for hashtag data
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HashtagResponseDTO {

    private Integer hashtagId;

    /** Tag text — lowercase, no # prefix. e.g. "springboot" */
    private String tag;

    /** Total posts using this tag */
    private Integer postCount;

    /** Last time this tag was used in a post */
    private LocalDateTime lastUsedAt;
}
