package com.connectsphere.comment.repository;

import com.connectsphere.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CommentRepository - Data Access Layer for Comment Entity
 *
 * Custom queries as per ConnectSphere case study section 4.3:
 *   findByPostId()          - All top-level comments for a post
 *   findByAuthorId()        - All comments by a user
 *   findByCommentId()       - Single comment lookup
 *   findByParentCommentId() - Replies to a specific comment
 *   findTopLevelByPostId()  - Only top-level (no parentCommentId) comments
 *   countByPostId()         - Comment count for post badge
 *   deleteByCommentId()     - Soft delete
 *   likeComment()           - Atomic like counter increment
 *   unlikeComment()         - Atomic like counter decrement
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    /**
     * Find a comment by its ID (including soft-deleted — needed for thread structure).
     * Used for getCommentById() — returns even deleted ones so thread isn't broken.
     */
    Optional<Comment> findByCommentId(Integer commentId);

    /**
     * Find a SPECIFIC comment by ID — only if NOT deleted.
     * Used when editing or replying (can't reply to a deleted comment).
     */
    Optional<Comment> findByCommentIdAndIsDeletedFalse(Integer commentId);

    /**
     * Get ALL comments for a post (including soft-deleted ones).
     * Ordered oldest first so the thread reads naturally top-to-bottom.
     * Soft-deleted ones are returned with content replaced in the service layer.
     */
    List<Comment> findByPostIdOrderByCreatedAtAsc(Integer postId);

    /**
     * Get only TOP-LEVEL comments for a post (parentCommentId IS NULL).
     * Used for initial post view — replies loaded on demand.
     */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.postId = :postId
            AND c.parentCommentId IS NULL
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findTopLevelByPostId(@Param("postId") Integer postId);

    /**
     * Get all REPLIES for a specific parent comment.
     * Two-level threading: replies to top-level comments only.
     */
    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(Integer parentCommentId);

    /**
     * Get all comments (including replies) written by a specific user.
     * Excludes soft-deleted to keep the user's profile clean.
     */
    List<Comment> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(Integer authorId);

    /**
     * Count total active (non-deleted) comments on a post.
     * Includes both top-level comments and replies.
     * Used for comment count badge on the post card.
     */
    int countByPostIdAndIsDeletedFalse(Integer postId);

    /**
     * Soft delete — sets isDeleted = true.
     * Comment is preserved in DB for thread structure integrity.
     * Content is replaced with "[This comment was deleted]" in the DTO layer.
     */
    @Modifying
    @Query("UPDATE Comment c SET c.isDeleted = true WHERE c.commentId = :commentId")
    void softDeleteByCommentId(@Param("commentId") Integer commentId);

    /**
     * Atomically increment likesCount by 1 on a comment.
     * Called by likeComment() service method.
     */
    @Modifying
    @Query("UPDATE Comment c SET c.likesCount = c.likesCount + 1 WHERE c.commentId = :commentId AND c.isDeleted = false")
    void incrementLikes(@Param("commentId") Integer commentId);

    /**
     * Atomically decrement likesCount by 1 (minimum 0).
     * Called by unlikeComment() service method.
     */
    @Modifying
    @Query("UPDATE Comment c SET c.likesCount = GREATEST(c.likesCount - 1, 0) WHERE c.commentId = :commentId AND c.isDeleted = false")
    void decrementLikes(@Param("commentId") Integer commentId);
}