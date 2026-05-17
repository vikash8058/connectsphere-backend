package com.connectsphere.post.dto;

import com.connectsphere.post.entity.Visibility;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * UpdatePostRequestDTO - Payload for PUT /posts/{postId}
 *
 * Content and visibility can be updated — media, postType are immutable after creation.
 * Visibility can also be changed via the dedicated PATCH /posts/{postId}/visibility endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePostRequestDTO {

    @Size(max = 5000, message = "Post content cannot exceed 5000 characters")
    private String content;

    private Visibility visibility;
}