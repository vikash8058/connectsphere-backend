package com.connectsphere.comment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

/**
 * PostResponseDTO - Minimal DTO to receive post data from post-service
 *
 * We only need basic fields to confirm post existence.
 * Fields match what post-service returns in its PostResponseDTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostResponseDTO {

    private Integer postId;
    private Integer authorId;
    private String content;
    private String visibility;
    private Integer likesCount;
    private Integer commentsCount;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}