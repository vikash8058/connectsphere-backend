package com.connectsphere.post.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * UpdatePostRequestDTO - Payload for PUT /posts/{postId}
 *
 * Only content can be updated — media, postType are immutable after creation.
 * Visibility is changed via the dedicated PATCH /posts/{postId}/visibility endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePostRequestDTO {

    @Size(max = 5000, message = "Post content cannot exceed 5000 characters")
    private String content;
}