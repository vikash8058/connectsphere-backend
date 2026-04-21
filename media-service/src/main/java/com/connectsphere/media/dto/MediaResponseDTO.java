package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MediaResponseDTO - Safe media data returned in API responses
 *
 * Returned after:
 *   - Successful file upload (/media/upload)
 *   - Fetching media by ID, by post, or by uploader
 *   - Linking media to a post
 *
 * The CDN url field is the key output — callers (post-service, frontend)
 * use this URL to reference the uploaded file in post/story creation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaResponseDTO {

    private Integer mediaId;
    private Integer uploaderId;

    /** Full CDN URL — use this in CreatePostRequest.mediaUrls or CreateStoryRequest.mediaUrl */
    private String url;

    private MediaType mediaType;

    /** File size in kilobytes */
    private Long sizeKb;

    /** Actual MIME type (image/jpeg, image/png, image/webp, video/mp4) */
    private String mimeType;

    /** Post ID this media is linked to (null if not yet linked) */
    private Integer linkedPostId;

    private LocalDateTime uploadedAt;
}
