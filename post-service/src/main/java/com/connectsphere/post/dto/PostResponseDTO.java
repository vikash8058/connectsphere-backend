package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

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
    private Boolean isElite;
}