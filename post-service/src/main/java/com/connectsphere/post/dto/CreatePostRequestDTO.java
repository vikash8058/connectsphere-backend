package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CreatePostRequestDTO - Payload for POST /posts
 *
 * Case study 2.3: "Create posts — text-only or with attached media (images, videos)"
 * Case study 2.3: "Set post visibility: Public, Followers-only, or Private"
 *
 * content    -> Main text body (required, max 2000 chars)
 * mediaUrls  -> List of pre-uploaded CDN URLs from media-service
 * postType   -> TEXT (default) / IMAGE / VIDEO
 * visibility -> PUBLIC (default) / FOLLOWERS_ONLY / PRIVATE
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostRequestDTO {

    @NotBlank(message = "Post content cannot be empty")
    @Size(min = 1, max = 2000, message = "Post content must be between 1 and 2000 characters")
    private String content;

    /** Pre-uploaded media CDN URLs from media-service */
    @Builder.Default
    private List<String> mediaUrls = new ArrayList<>();

    /** Defaults to TEXT if not provided */
    @Builder.Default
    private PostType postType = PostType.TEXT;

    /** Defaults to PUBLIC if not provided */
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;
}