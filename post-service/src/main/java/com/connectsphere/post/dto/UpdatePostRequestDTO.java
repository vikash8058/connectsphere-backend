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

    @Size(min = 1, max = 2000, message = "Post content must be between 1 and 2000 characters")
    private String content;
}