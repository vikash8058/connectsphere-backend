package com.connectsphere.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * AddCommentRequestDTO - Payload for POST /comments
 *
 * postId          -> (required) Which post this comment belongs to
 * parentCommentId -> (optional) null = top-level, set = reply to a comment
 * content         -> (required) Text of the comment
 *
 * authorId is NOT in this DTO — it comes from the JWT token.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddCommentRequestDTO {

    @NotNull(message = "postId is required")
    private Integer postId;

    /**
     * null = top-level comment on the post
     * set  = reply to this parentCommentId
     */
    private Integer parentCommentId;

    @NotBlank(message = "Comment content cannot be empty")
    @Size(min = 1, max = 1000, message = "Comment must be between 1 and 1000 characters")
    private String content;
}