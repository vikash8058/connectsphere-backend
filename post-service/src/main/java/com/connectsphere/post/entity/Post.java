package com.connectsphere.post.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Post Entity - Maps to 'posts' table in connectsphere_post database
 *
 * Fields as per ConnectSphere case study section 4.2 (Post-Service):
 *   postId        - Primary key, auto-increment
 *   authorId      - FK to users.user_id in auth-service (not a DB FK - cross-service)
 *   content       - The text body of the post
 *   mediaUrls     - List of CDN URLs for attached images / video (stored as JSON column)
 *   postType      - TEXT / IMAGE / VIDEO
 *   visibility    - PUBLIC / FOLLOWERS_ONLY / PRIVATE
 *   likesCount    - Denormalised counter (incremented by like-service)
 *   commentsCount - Denormalised counter (incremented by comment-service)
 *   sharesCount   - Denormalised counter (incremented when a user reposts)
 *   isDeleted     - Soft delete flag (true = hidden from public, preserved for audit)
 *   createdAt     - Auto-set on INSERT by Hibernate
 *   updatedAt     - Auto-updated by Hibernate on every UPDATE
 */
@Entity
@Table(
    name = "posts",
    indexes = {
        @Index(name = "idx_author_id",     columnList = "author_id"),
        @Index(name = "idx_visibility",    columnList = "visibility"),
        @Index(name = "idx_created_at",    columnList = "created_at"),
        @Index(name = "idx_is_deleted",    columnList = "is_deleted")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Integer postId;

    /**
     * ID of the user who created this post.
     * References users.user_id in auth-service.
     * Not a DB foreign key — cross-service relationship.
     */
    @Column(name = "author_id", nullable = false)
    private Integer authorId;

    /**
     * Main text content of the post.
     * May contain @mentions and #hashtags which are parsed by search-service.
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * List of CDN URLs for attached media (images/videos).
     * Stored as JSON array in the database column.
     * Empty list for TEXT posts.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "post_media_urls",
        joinColumns = @JoinColumn(name = "post_id")
    )
    @Column(name = "media_url", length = 500)
    @Builder.Default
    private List<String> mediaUrls = new ArrayList<>();

    /**
     * Type of post content: TEXT / IMAGE / VIDEO
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 10)
    @Builder.Default
    private PostType postType = PostType.TEXT;

    /**
     * Visibility setting - controls audience:
     *   PUBLIC         -> everyone (including guests)
     *   FOLLOWERS_ONLY -> only approved followers
     *   PRIVATE        -> only the author
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    /**
     * Denormalised like count.
     * Incremented / decremented by like-service via REST call.
     * Avoids expensive COUNT(*) JOIN on every feed render.
     */
    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    /**
     * Denormalised comment count.
     * Incremented / decremented by comment-service via REST call.
     */
    @Column(name = "comments_count", nullable = false)
    @Builder.Default
    private Integer commentsCount = 0;

    /**
     * Denormalised share/repost count.
     * Incremented when another user shares/reposts this post.
     */
    @Column(name = "shares_count", nullable = false)
    @Builder.Default
    private Integer sharesCount = 0;

    /**
     * Soft-delete flag.
     * true  = post is deleted, hidden from all public queries
     * false = post is active and visible (subject to visibility setting)
     *
     * Deleted posts are retained in DB for audit trail (NFR: 30-day retention).
     * Media URLs on soft-deleted posts are also soft-deleted in media-service.
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /** Auto-set on INSERT */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Auto-updated by Hibernate on every UPDATE */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}