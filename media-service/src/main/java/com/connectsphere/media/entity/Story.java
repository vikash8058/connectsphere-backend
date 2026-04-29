package com.connectsphere.media.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(
    name = "stories",
    indexes = {
        @Index(name = "idx_story_author_id",  columnList = "author_id"),
        @Index(name = "idx_story_expires_at", columnList = "expires_at"),
        @Index(name = "idx_story_is_active",  columnList = "is_active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "story_id")
    private Integer storyId;

    /**
     * ID of the user who created this story.
     * References users.user_id in auth-service. Not a DB foreign key — cross-service.
     */
    @Column(name = "author_id", nullable = false)
    private Integer authorId;

    /**
     * CDN URL of the story media file.
     * Points to an already-uploaded media item on the CDN.
     */
    @Column(name = "media_url", nullable = false, length = 1000)
    private String mediaUrl;

    /**
     * Optional text caption displayed under the story media.
     * Max 500 characters.
     */
    @Column(name = "caption", length = 500)
    private String caption;

    /**
     * Type of media in this story: IMAGE or VIDEO.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    /**
     * View count — incremented atomically each time another user opens this story.
     * Tracks story engagement; NOT incremented when the author views their own story.
     */
    @Column(name = "views_count", nullable = false)
    @Builder.Default
    private Integer viewsCount = 0;

    /**
     * Expiry timestamp = createdAt + 24 hours.
     * Set at creation time by MediaServiceImpl.
     * StoryExpiryScheduler reads this field to mark stories inactive.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Auto-set on INSERT by Hibernate.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Active flag.
     * true  = story is live and visible to followers
     * false = story has expired (set by StoryExpiryScheduler) or was deleted by the author
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Visibility control - PUBLIC, FOLLOWERS_ONLY, or PRIVATE.
     * Defaults to PUBLIC if not specified.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;
}
