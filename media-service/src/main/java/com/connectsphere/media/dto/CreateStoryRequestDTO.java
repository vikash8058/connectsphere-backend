package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStoryRequestDTO {

    /**
     * CDN URL of the story media.
     * Must be obtained from /media/upload first.
     * e.g. "https://cdn.connectsphere.com/media/2026/04/uuid.jpg"
     */
    @NotBlank(message = "Media URL is required for a story")
    @Size(max = 1000, message = "Media URL must not exceed 1000 characters")
    private String mediaUrl;

    /**
     * Optional text caption shown under the story media.
     */
    @Size(max = 500, message = "Caption must not exceed 500 characters")
    private String caption;

    /**
     * Type of media in this story: IMAGE or VIDEO.
     */
    @NotNull(message = "Media type is required (IMAGE or VIDEO)")
    private MediaType mediaType;

    /**
     * Visibility control - PUBLIC, FOLLOWERS_ONLY, or PRIVATE.
     * Optional: defaults to PUBLIC on server if null.
     */
    private com.connectsphere.media.entity.Visibility visibility;
}
