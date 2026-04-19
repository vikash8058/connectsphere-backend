package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PostResponseDTO - Safe post data returned in API responses
 *
 * Never return isDeleted=true posts in public endpoints.
 * This DTO is used for all post-related responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponseDTO {

    private Integer postId;
    private Integer authorId;
    private String content;
    private List<String> mediaUrls;
    private PostType postType;
    private Visibility visibility;
    private Integer likesCount;
    private Integer commentsCount;
    private Integer sharesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}