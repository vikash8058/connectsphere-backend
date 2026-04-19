package com.connectsphere.post.repository;

import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.Visibility;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PostRepository - Data Access Layer for Post Entity
 *
 * Custom queries as per ConnectSphere case study section 4.2:
 *   findByAuthorId()                    - Get all posts by a specific user
 *   findByPostId()                      - Get single post by ID
 *   findByVisibility()                  - Public feed (guest browsing)
 *   findFeedByUserIds()                 - Personalised news feed (followed users)
 *   searchByContent()                   - Keyword search in post content
 *   findByAuthorIdOrderByCreatedAtDesc()- User's own timeline, newest first
 *   countByAuthorId()                   - Post count for profile badge
 *   incrementLikes / decrementLikes     - Atomic counter updates
 *   incrementComments / decrementComments
 *   incrementShares
 *   softDeleteByPostId                  - Soft delete (isDeleted = true)
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Integer> {

    /**
     * Find all active posts by a specific author, newest first.
     * Used for "View User Timeline" and profile page.
     */
    List<Post> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(Integer authorId);

    /**
     * Find a single post by postId — only if not soft-deleted.
     * Used for "View Post Detail".
     */
    Optional<Post> findByPostIdAndIsDeletedFalse(Integer postId);

    /**
     * Public feed — all PUBLIC posts that are not deleted, newest first.
     * Used for guest browsing and explore feed.
     */
    List<Post> findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(Visibility visibility);

    /**
     * Personalised news feed.
     * Returns posts from all followees the current user follows.
     * Includes PUBLIC and FOLLOWERS_ONLY posts only (not PRIVATE).
     * Ordered newest first.
     *
     * authorIds = list of userId values the current user follows
     * (provided by follow-service via inter-service call)
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.authorId IN :authorIds
            AND p.isDeleted = false
            AND p.visibility IN ('PUBLIC', 'FOLLOWERS_ONLY')
            ORDER BY p.createdAt DESC
            """)
    List<Post> findFeedByUserIds(@Param("authorIds") List<Integer> authorIds);

    /**
     * Full-text keyword search in post content.
     * Case-insensitive LIKE query.
     * Only PUBLIC posts that are not deleted.
     * Used by SearchService and the search bar.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
            AND p.isDeleted = false
            AND p.visibility = 'PUBLIC'
            ORDER BY p.createdAt DESC
            """)
    List<Post> searchByContent(@Param("keyword") String keyword);

    /**
     * Count total posts by an author (non-deleted).
     * Displayed as the post count badge on the user profile page.
     */
    int countByAuthorIdAndIsDeletedFalse(Integer authorId);

    /**
     * Atomically increment likesCount by 1.
     * Called by like-service after a successful like operation.
     * Uses UPDATE query to avoid loading the full entity.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + 1 WHERE p.postId = :postId AND p.isDeleted = false")
    void incrementLikes(@Param("postId") Integer postId);

    /**
     * Atomically decrement likesCount by 1 (minimum 0).
     * Called by like-service after a successful unlike operation.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.likesCount = GREATEST(p.likesCount - 1, 0) WHERE p.postId = :postId AND p.isDeleted = false")
    void decrementLikes(@Param("postId") Integer postId);

    /**
     * Atomically increment commentsCount by 1.
     * Called by comment-service after addComment().
     */
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.commentsCount = p.commentsCount + 1 WHERE p.postId = :postId AND p.isDeleted = false")
    void incrementComments(@Param("postId") Integer postId);

    /**
     * Atomically decrement commentsCount by 1 (minimum 0).
     * Called by comment-service after deleteComment().
     */
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.commentsCount = GREATEST(p.commentsCount - 1, 0) WHERE p.postId = :postId AND p.isDeleted = false")
    void decrementComments(@Param("postId") Integer postId);

    /**
     * Atomically increment sharesCount by 1.
     * Called when another user shares/reposts this post.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.sharesCount = p.sharesCount + 1 WHERE p.postId = :postId AND p.isDeleted = false")
    void incrementShares(@Param("postId") Integer postId);

    /**
     * Soft delete — sets isDeleted = true.
     * Post record is preserved for 30-day audit trail (NFR).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.isDeleted = true WHERE p.postId = :postId")
    void softDeleteByPostId(@Param("postId") Integer postId);

    /**
     * Change visibility of a post.
     * Called by the PATCH /posts/{postId}/visibility endpoint.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.visibility = :visibility WHERE p.postId = :postId AND p.isDeleted = false")
    void updateVisibility(@Param("postId") Integer postId,
                          @Param("visibility") Visibility visibility);
}