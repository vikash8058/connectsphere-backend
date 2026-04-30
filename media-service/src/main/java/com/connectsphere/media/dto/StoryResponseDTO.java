package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import com.connectsphere.media.entity.Visibility;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryResponseDTO {

    private Integer storyId;
    private Integer authorId;

    /** CDN URL of the story media content */
    private String mediaUrl;

    /** Optional text caption */
    private String caption;

    /** IMAGE or VIDEO */
    @NotNull(message = "Media type is required (IMAGE or VIDEO)")
    private MediaType mediaType;

    /** Number of unique user views (not counting author's own views) */
    private Integer viewsCount;

    private String authorUsername;
    private String authorProfilePic;

    /** Expiry timestamp = createdAt + 24 hours */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    /** Visibility control - PUBLIC, FOLLOWERS_ONLY, or PRIVATE. */
    private Visibility visibility;

    /** false = expired or deleted; true = live */
    private Boolean isActive;
}
