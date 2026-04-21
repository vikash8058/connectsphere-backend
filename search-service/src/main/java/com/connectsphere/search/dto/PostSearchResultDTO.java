package com.connectsphere.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PostSearchResultDTO - Enriched post result returned by searchPosts()
 *
 * Combines postId (from PostHashtag index) with full post data
 * fetched from post-service via Feign.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostSearchResultDTO {

    private Integer postId;
    private Integer authorId;
    private String authorUsername;
    private String content;
    private List<String> mediaUrls;
    private String postType;
    private String visibility;
    private Integer likesCount;
    private Integer commentsCount;
    private Integer sharesCount;
    private List<String> hashtags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
