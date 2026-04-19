package com.connectsphere.comment.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * CommentResponseDTO - Safe comment data returned in API responses
 *
 * Key rule: if isDeleted = true, content is replaced with
 * "[This comment was deleted]" to preserve thread structure
 * while hiding the original text.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponseDTO {

    private Integer commentId;
    private Integer postId;
    private Integer authorId;
    private Integer parentCommentId;  // null for top-level comments
    private String content;           // "[This comment was deleted]" if soft-deleted
    private Integer likesCount;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}