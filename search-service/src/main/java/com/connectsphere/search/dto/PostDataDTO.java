package com.connectsphere.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PostDataDTO - Post data returned by post-service Feign client
 * Maps to PostResponseDTO in post-service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostDataDTO {

    private Integer postId;
    private Integer authorId;
    private String content;
    private List<String> mediaUrls;
    private String postType;
    private String visibility;
    private Integer likesCount;
    private Integer commentsCount;
    private Integer sharesCount;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
