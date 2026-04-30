package com.connectsphere.like.service;

import com.connectsphere.like.dto.*;
import com.connectsphere.like.entity.TargetType;

import java.util.List;


public interface LikeService {

    /**
     * Add a reaction to a post or comment.
     * Enforces one-reaction-per-user via DB unique constraint.
     * After success, calls the target service to increment its likesCount counter.
     */
    ApiResponseDTO<LikeResponseDTO> likeTarget(Integer userId, LikeRequestDTO request);

    /**
     * Remove a user's reaction from a post or comment.
     * After success, calls the target service to decrement its likesCount counter.
     */
    ApiResponseDTO<String> unlikeTarget(Integer userId, Integer targetId, TargetType targetType);

    /**
     * Check whether a user has already reacted to a target.
     * Used to toggle the like button UI state.
     */
    ApiResponseDTO<Boolean> hasLiked(Integer userId, Integer targetId, TargetType targetType);

    /**
     * Get all reactions on a specific post or comment.
     * Used for showing who liked/reacted to a post.
     */
    ApiResponseDTO<List<LikeResponseDTO>> getLikesByTarget(Integer targetId, TargetType targetType);

    /**
     * Get all reactions made by a specific user.
     * Used for user activity history.
     */
    ApiResponseDTO<List<LikeResponseDTO>> getLikesByUser(Integer userId);

    /**
     * Total reaction count on a target (all types summed).
     * Used for the like count badge on posts/comments.
     */
    ApiResponseDTO<Integer> getLikeCount(Integer targetId, TargetType targetType);

    /**
     * Count of a specific reaction type on a target.
     * e.g., how many LOVE reactions on post 42.
     */
    ApiResponseDTO<Integer> getLikeCountByType(Integer targetId, TargetType targetType, String reactionType);

    /**
     * Full reaction summary map for the emoji reaction bar.
     * Returns counts for all 6 reaction types in one call.
     */
    ApiResponseDTO<ReactionSummaryDTO> getReactionSummary(Integer targetId, TargetType targetType);

    /**
     * Change an existing reaction to a different type.
     * Atomic: delete old + insert new in one @Transactional.
     * No net change to likesCount counter (still one reaction).
     */
    ApiResponseDTO<LikeResponseDTO> changeReaction(Integer userId, ChangeReactionRequestDTO request);

    /**
     * Get the specific reaction a user has on a target (if any).
     */
    ApiResponseDTO<LikeResponseDTO> getUserReaction(Integer userId, Integer targetId, TargetType targetType);
}