package com.connectsphere.comment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Comment Entity - Maps to 'comments' table in connectsphere_comment database
 *
 * Fields as per ConnectSphere case study section 4.3 (Comment-Service):
 *   commentId       - Primary key, auto-increment
 *   postId          - ID of the post this comment belongs to (cross-service ref)
 *   authorId        - ID of the user who wrote the comment (cross-service ref)
 *   parentCommentId - null for top-level comments, set for replies
 *   content         - Text content of the comment
 *   likesCount      - Denormalised counter (liked by like-service or inline logic)
 *   isDeleted       - Soft-delete flag (true = deleted but thread preserved)
 *   createdAt       - Auto-set on INSERT
 *   updatedAt       - Auto-updated by Hibernate
 *
 * Two-level threading model:
 *   - Top-level comment: parentCommentId = null
 *   - Reply:             parentCommentId = <commentId of the parent comment>
 */
@Entity
@Table(
    name = "comments",
    indexes = {
        @Index(name = "idx_post_id",            columnList = "post_id"),
        @Index(name = "idx_author_id",          columnList = "author_id"),
        @Index(name = "idx_parent_comment_id",  columnList = "parent_comment_id"),
        @Index(name = "idx_is_deleted",         columnList = "is_deleted")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Integer commentId;

    /**
     * Post this comment belongs to.
     * References posts.post_id in post-service.
     * Not a DB foreign key — cross-service relationship.
     */
    @Column(name = "post_id", nullable = false)
    private Integer postId;

    /**
     * User who wrote this comment.
     * References users.user_id in auth-service.
     * Not a DB foreign key — cross-service relationship.
     */
    @Column(name = "author_id", nullable = false)
    private Integer authorId;

    /**
     * Parent comment ID for threaded replies.
     *   null  = this is a top-level comment on the post
     *   value = this is a reply to the comment with this ID
     *
     * Only two levels supported (case study section 4.3).
     */
    @Column(name = "parent_comment_id")
    private Integer parentCommentId;

    /**
     * Text content of the comment.
     * Can contain @mentions which trigger MENTION notifications.
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Denormalised like count for this comment.
     * Incremented / decremented inline in CommentService
     * (not a separate like-service call for comment likes in this implementation).
     */
    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    /**
     * Soft-delete flag.
     * true  = comment deleted but thread structure is preserved
     * false = comment is active and visible
     *
     * Deleted comment content is replaced with
     * "[This comment was deleted]" in the response DTO.
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