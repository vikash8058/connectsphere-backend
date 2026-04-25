package com.connectsphere.media.repository;

import com.connectsphere.media.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * StoryRepository - Data Access Layer for Story Entity
 *
 * Custom queries as per ConnectSphere case study section 4.7:
 *   findActiveStoriesByAuthorIds() - Stories from a list of followed users (news feed)
 *   findByStoryId()                - Single active story by ID
 *   findByAuthorId()               - All stories by a specific user
 *   findExpiredStories()           - Stories that have passed their expiresAt (for scheduler)
 *   incrementViewsCount()          - Atomically increment view count
 *   deactivateExpiredStories()     - Batch set isActive=false for expired stories
 *   deactivateByStoryId()          - Manual story deletion by author
 */
@Repository
public interface StoryRepository extends JpaRepository<Story, Integer> {

    /**
     * Find all active stories with strict privacy filtering:
     * 1. Your own stories (any visibility)
     * 2. Followees' stories (Public or Followers-only)
     * 3. Global stories (Public only)
     */
    @Query("""
            SELECT s FROM Story s
            WHERE (
                s.authorId = :userId 
                OR (s.authorId IN :followeeIds AND s.visibility != 'PRIVATE')
                OR s.visibility = 'PUBLIC'
            )
            AND s.isActive = true
            ORDER BY s.createdAt DESC
            """)
    List<Story> findActiveStoriesForFeed(
            @Param("userId") Integer userId, 
            @Param("followeeIds") List<Integer> followeeIds);

    /**
     * Find a single active story by ID.
     * Only returns the story if it is still active (not expired/deleted).
     */
    Optional<Story> findByStoryIdAndIsActiveTrue(Integer storyId);

    /**
     * Find all stories by a specific author (including expired, for their own profile).
     * Ordered newest first.
     */
    List<Story> findByAuthorIdOrderByCreatedAtDesc(Integer authorId);

    /**
     * Find all active stories by a specific author.
     * Used for "view my stories" and profile story ring display.
     */
    List<Story> findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(Integer authorId);

    /**
     * Find all stories that have passed their expiresAt timestamp but are still marked active.
     * Called by StoryExpiryScheduler every 5 minutes (NFR: purge within 5 min of 24h expiry).
     */
    @Query("""
            SELECT s FROM Story s
            WHERE s.expiresAt <= :now
            AND s.isActive = true
            """)
    List<Story> findExpiredActiveStories(@Param("now") LocalDateTime now);

    /**
     * Batch deactivate all stories that have expired.
     * Called by StoryExpiryScheduler — single UPDATE query for efficiency.
     * NFR: "Stories expire exactly 24 hours after creation"
     */
    @Modifying
    @Query("""
            UPDATE Story s
            SET s.isActive = false
            WHERE s.expiresAt <= :now
            AND s.isActive = true
            """)
    int deactivateExpiredStories(@Param("now") LocalDateTime now);

    /**
     * Deactivate (soft-delete) a single story by ID.
     * Called when the author deletes their own story before it expires.
     */
    @Modifying
    @Query("UPDATE Story s SET s.isActive = false WHERE s.storyId = :storyId")
    void deactivateByStoryId(@Param("storyId") Integer storyId);

    /**
     * Atomically increment view count by 1.
     * Called when another user opens a story.
     * Only increments if story is still active.
     */
    @Modifying
    @Query("""
            UPDATE Story s
            SET s.viewsCount = s.viewsCount + 1
            WHERE s.storyId = :storyId
            AND s.isActive = true
            """)
    void incrementViewsCount(@Param("storyId") Integer storyId);

    /**
     * Check if a story belongs to a specific author (ownership check).
     */
    boolean existsByStoryIdAndAuthorId(Integer storyId, Integer authorId);

    /**
     * Count total active stories by an author.
     * Displayed on the author's profile for story ring indicator.
     */
    int countByAuthorIdAndIsActiveTrue(Integer authorId);
}
