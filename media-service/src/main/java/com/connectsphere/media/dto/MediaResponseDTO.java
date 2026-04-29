package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import lombok.*;

import java.time.LocalDateTime;


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
