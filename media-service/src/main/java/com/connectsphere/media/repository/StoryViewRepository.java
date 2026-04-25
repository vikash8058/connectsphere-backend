package com.connectsphere.media.repository;

import com.connectsphere.media.entity.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryViewRepository extends JpaRepository<StoryView, Long> {

    /**
     * Check if a specific user has already viewed a story.
     */
    boolean existsByStoryAndViewerUserId(com.connectsphere.media.entity.Story story, Integer viewerUserId);

    /**
     * Get all viewer user IDs for a specific story.
     */
    @Query("SELECT sv.viewerUserId FROM StoryView sv WHERE sv.story.storyId = :storyId ORDER BY sv.viewedAt DESC")
    List<Integer> findViewerUserIdsByStoryId(@Param("storyId") Integer storyId);

    /**
     * Count unique viewers for a story.
     */
    long countByStoryStoryId(Integer storyId);
}
