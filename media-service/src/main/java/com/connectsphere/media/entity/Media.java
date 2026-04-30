package com.connectsphere.media.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(
    name = "media",
    indexes = {
        @Index(name = "idx_media_uploader_id",   columnList = "uploader_id"),
        @Index(name = "idx_media_linked_post_id", columnList = "linked_post_id"),
        @Index(name = "idx_media_type",           columnList = "media_type"),
        @Index(name = "idx_media_is_deleted",     columnList = "is_deleted")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Integer mediaId;

    /**
     * ID of the user who uploaded this file.
     * References users.user_id in auth-service. Not a DB foreign key — cross-service.
     */
    @Column(name = "uploader_id", nullable = false)
    private Integer uploaderId;

    /**
     * Full CDN URL where the media file is accessible.
     * e.g. https://cdn.connectsphere.com/media/2026/04/uuid-filename.jpg
     * In production this points to AWS S3 / CloudFront.
     */
    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    /**
     * Type of media: IMAGE or VIDEO
     * Determines which size/type validation rules apply.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    /**
     * File size in kilobytes.
     * Used for analytics and enforcing NFR upload size limits.
     */
    @Column(name = "size_kb", nullable = false)
    private Long sizeKb;

    /**
     * Actual MIME type of the uploaded file.
     * Validated against allowlist: image/jpeg, image/png, image/webp, video/mp4.
     */
    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    /**
     * ID of the post this media is linked to.
     * Nullable — media is uploaded first, then linked to a post upon post creation.
     * Set by post-service or directly via linkToPost endpoint.
     */
    @Column(name = "linked_post_id")
    private Integer linkedPostId;

    /**
     * Soft-delete flag.
     * true  = media is soft-deleted (post was deleted; retained for 30-day audit trail)
     * false = media is active
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * Timestamp of upload. Auto-set on INSERT by Hibernate.
     */
    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
}
