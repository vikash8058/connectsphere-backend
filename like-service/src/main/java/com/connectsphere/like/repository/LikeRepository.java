package com.connectsphere.like.repository;

import com.connectsphere.like.entity.Like;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LikeRepository - Data Access Layer for Like Entity
 *
 * Custom queries as per ConnectSphere case study section 4.4:
 *   findByUserIdAndTargetId()             - Check if user reacted to a target
 *   findByTargetId()                      - All reactions on a target
 *   findByUserId()                        - All reactions by a user
 *   findByTargetIdAndTargetType()         - Reactions on a specific post/comment
 *   existsByUserIdAndTargetId()           - Boolean check for hasLiked()
 *   countByTargetId()                     - Total reactions on a target
 *   countByTargetIdAndTargetType()        - Total on a specific post/comment
 *   deleteByUserIdAndTargetId()           - Unlike (remove reaction)
 *   countByReactionTypeAndTargetId()      - Count by emoji type for reaction bars
 */
@Repository
public interface LikeRepository extends JpaRepository<Like, Integer> {

    /**
     * Find a specific user's reaction on a specific target (any type).
     * Used by hasLiked() and changeReaction().
     */
    Optional<Like> findByUserIdAndTargetIdAndTargetType(
            Integer userId, Integer targetId, TargetType targetType);

    /**
     * All reactions on a given target (post or comment) by targetId + targetType.
     * Used to display who reacted to a post/comment.
     */
    List<Like> findByTargetIdAndTargetType(Integer targetId, TargetType targetType);

    /**
     * All reactions made by a specific user.
     * Used for user activity history.
     */
    List<Like> findByUserId(Integer userId);

    /**
     * Check if a user has already reacted to a target.
     * Returns true if a Like record exists for (userId, targetId, targetType).
     */
    boolean existsByUserIdAndTargetIdAndTargetType(
            Integer userId, Integer targetId, TargetType targetType);

    /**
     * Total reaction count on a target (all reaction types combined).
     * Used for like count badge on posts/comments.
     */
    int countByTargetIdAndTargetType(Integer targetId, TargetType targetType);

    /**
     * Count reactions of a specific type on a target.
     * Used to build the reaction summary map (emoji bar):
     *   { "LIKE": 10, "LOVE": 5, "HAHA": 2, ... }
     */
    int countByTargetIdAndTargetTypeAndReactionType(
            Integer targetId, TargetType targetType, ReactionType reactionType);

    /**
     * Remove a user's reaction from a target (unlike).
     * Called by unlikeTarget() — atomic DELETE.
     */
    @Modifying
    @Query("DELETE FROM Like l WHERE l.userId = :userId AND l.targetId = :targetId AND l.targetType = :targetType")
    void deleteByUserIdAndTargetIdAndTargetType(
            @Param("userId") Integer userId,
            @Param("targetId") Integer targetId,
            @Param("targetType") TargetType targetType);
}