package com.connectsphere.follow.service;

import com.connectsphere.follow.dto.*;

import java.util.List;


public interface FollowService {

    /**
     * Follow a user.
     * Creates an ACTIVE follow record (or PENDING if target has private account).
     * Prevents duplicate follows via DB unique constraint.
     * A user cannot follow themselves.
     */
    ApiResponseDTO<FollowResponseDTO> follow(Integer followerId, Integer followeeId);

    /**
     * Unfollow a user.
     * Deletes the follow record from the DB.
     * Throws FollowNotFoundException if not currently following.
     */
    ApiResponseDTO<String> unfollow(Integer followerId, Integer followeeId);

    /**
     * Check if followerId is currently following followeeId.
     * Used by post-service to validate FOLLOWERS_ONLY post access.
     * Used by UI to toggle the Follow/Unfollow button state.
     */
    ApiResponseDTO<Boolean> isFollowing(Integer followerId, Integer followeeId);

    /**
     * Get the list of users who follow the given user.
     * Returns list of FollowResponseDTO (includes followerId, createdAt).
     */
    ApiResponseDTO<List<FollowResponseDTO>> getFollowers(Integer userId);

    /**
     * Get the list of users the given user is following.
     * Returns list of FollowResponseDTO (includes followeeId, createdAt).
     */
    ApiResponseDTO<List<FollowResponseDTO>> getFollowing(Integer userId);

    /**
     * Get follower count only for a user.
     */
    ApiResponseDTO<Integer> getFollowerCount(Integer userId);

    /**
     * Get following count only for a user.
     */
    ApiResponseDTO<Integer> getFollowingCount(Integer userId);

    /**
     * Get both follower and following counts in a single call.
     * Used for the profile page header badges.
     */
    ApiResponseDTO<FollowCountDTO> getFollowCounts(Integer userId);

    /**
     * Get mutual follows — users that follow each other.
     * Returns list of userIds who are mutual connections.
     * Used for "Mutual connections" section on profiles.
     */
    ApiResponseDTO<List<Integer>> getMutualFollows(Integer userId);

    /**
     * Get suggested users to follow.
     * Returns userIds of second-degree connections not already followed.
     * "People You May Know" feature.
     */
    ApiResponseDTO<List<Integer>> getSuggestedUsers(Integer userId);

    /**
     * Get just the followee IDs for a user.
     * Lightweight call used by post-service to build the news feed.
     * Returns List<Integer> (not full FollowResponseDTO) for efficiency.
     */
    ApiResponseDTO<List<Integer>> getFolloweeIds(Integer followerId);
}