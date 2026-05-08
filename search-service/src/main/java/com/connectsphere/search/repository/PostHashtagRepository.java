package com.connectsphere.search.repository;

import com.connectsphere.search.entity.PostHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostHashtagRepository extends JpaRepository<PostHashtag, Integer> {

    /**
     * Find all postIds that are tagged with a specific hashtag.
     * Used to render the hashtag feed page: /hashtags/{tag}/posts
     */
    @Query("SELECT ph.postId FROM PostHashtag ph WHERE ph.hashtag.tag = :tag ORDER BY ph.createdAt DESC")
    List<Integer> findPostIdsByHashtagTag(@Param("tag") String tag);

    /**
     * Find all hashtag IDs associated with a specific post.
     * Used to retrieve the tags on a post.
     */
    @Query("SELECT ph.hashtag.hashtagId FROM PostHashtag ph WHERE ph.postId = :postId")
    List<Integer> findHashtagIdsByPostId(@Param("postId") Integer postId);

    /**
     * Find all PostHashtag mappings for a post — includes full Hashtag entity.
     * Used when removing a post to decrement each hashtag's postCount.
     */
    List<PostHashtag> findByPostId(Integer postId);

    /**
     * Check if a specific post-hashtag mapping already exists.
     * Deduplication guard before inserting.
     */
    @Query("SELECT COUNT(ph) > 0 FROM PostHashtag ph WHERE ph.postId = :postId AND ph.hashtag.hashtagId = :hashtagId")
    boolean existsByPostIdAndHashtagId(@Param("postId") Integer postId,
                                       @Param("hashtagId") Integer hashtagId);

    /**
     * Delete all hashtag mappings for a post.
     * Called when a post is deleted (hard removal of index entries).
     */
    @Modifying
    @Query("DELETE FROM PostHashtag ph WHERE ph.postId = :postId")
    void deleteByPostId(@Param("postId") Integer postId);

    /**
     * Delete a specific post-hashtag mapping by postId and hashtagId.
     * Called during reIndexPost when a specific tag is removed from a post,
     * so only that mapping is deleted (not all mappings for the post).
     */
    @Modifying
    @Query("DELETE FROM PostHashtag ph WHERE ph.postId = :postId AND ph.hashtag.hashtagId = :hashtagId")
    void deleteByPostIdAndHashtagId(@Param("postId") Integer postId,
                                    @Param("hashtagId") Integer hashtagId);

    /**
     * Count how many posts use a specific hashtag (by hashtagId).
     */
    @Query("SELECT COUNT(ph) FROM PostHashtag ph WHERE ph.hashtag.hashtagId = :hashtagId")
    long countByHashtagId(@Param("hashtagId") Integer hashtagId);
}