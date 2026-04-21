package com.connectsphere.search.repository;

import com.connectsphere.search.entity.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * HashtagRepository - Data access layer for Hashtag entity
 *
 * Methods as per ConnectSphere case study section 4.8:
 *   findByTag()              - Exact tag lookup (for upsert)
 *   findTrendingHashtags()   - Top N by postCount DESC
 *   searchByTagContaining()  - Autocomplete / partial tag search
 *   countPostsByHashtag()    - Count posts using a specific tag
 *
 * Additional methods:
 *   incrementPostCount()     - Atomic increment when a post uses this tag
 *   decrementPostCount()     - Atomic decrement when a post is deleted
 */
@Repository
public interface HashtagRepository extends JpaRepository<Hashtag, Integer> {

    /**
     * Find a hashtag by its exact tag string (case-sensitive, stored lowercase).
     * Used for upsert logic: if exists → increment, else → insert new.
     */
    Optional<Hashtag> findByTag(String tag);

    /**
     * Find top N trending hashtags ordered by postCount DESC.
     * Used for trending section on homepage and search page.
     *
     * @param limit max number of hashtags to return
     */
    @Query("SELECT h FROM Hashtag h WHERE h.postCount > 0 ORDER BY h.postCount DESC")
    List<Hashtag> findTrendingHashtags(org.springframework.data.domain.Pageable pageable);

    /**
     * Partial tag search — for autocomplete in hashtag input fields.
     * e.g. searching "spring" returns "spring", "springboot", "springframework"
     */
    List<Hashtag> findByTagContainingIgnoreCaseOrderByPostCountDesc(String tag);

    /**
     * Atomic increment of postCount — avoids loading the full entity.
     * Called when a new post containing this tag is indexed.
     */
    @Modifying
    @Query("UPDATE Hashtag h SET h.postCount = h.postCount + 1 WHERE h.hashtagId = :hashtagId")
    void incrementPostCount(@Param("hashtagId") Integer hashtagId);

    /**
     * Atomic decrement of postCount — called when a post is deleted.
     * Ensures postCount never goes below 0.
     */
    @Modifying
    @Query("UPDATE Hashtag h SET h.postCount = GREATEST(0, h.postCount - 1) WHERE h.hashtagId = :hashtagId")
    void decrementPostCount(@Param("hashtagId") Integer hashtagId);

    /**
     * Count total posts using a specific hashtag (by tag string).
     * Used by getHashtagCount() endpoint.
     */
    @Query("SELECT h.postCount FROM Hashtag h WHERE h.tag = :tag")
    Optional<Integer> findPostCountByTag(@Param("tag") String tag);

    /**
     * Check if a hashtag exists by tag string.
     */
    boolean existsByTag(String tag);
}
