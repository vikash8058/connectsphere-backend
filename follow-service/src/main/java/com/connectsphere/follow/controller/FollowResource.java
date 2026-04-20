package com.connectsphere.follow.controller;

import com.connectsphere.follow.dto.*;
import com.connectsphere.follow.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FollowResource - REST Controller for ConnectSphere Follow Service
 *
 * Base path: /api/v1/follows (via context-path in application.yml)
 *
 * PUBLIC (no JWT required):
 *   GET /follows/{userId}/followers         - Who follows this user
 *   GET /follows/{userId}/following         - Who this user follows
 *   GET /follows/{userId}/follower-count    - Follower count
 *   GET /follows/{userId}/following-count   - Following count
 *   GET /follows/{userId}/counts            - Both counts in one call
 *   GET /follows/{userId}/mutual            - Mutual connections
 *   GET /follows/{userId}/followee-ids      - Just followee IDs (for post-service)
 *
 * PROTECTED (JWT required):
 *   POST   /follows/{followeeId}            - Follow a user
 *   DELETE /follows/{followeeId}            - Unfollow a user
 *   GET    /follows/check/{followeeId}      - Is current user following this user?
 *   GET    /follows/suggestions             - Suggested users for current user
 *
 * followerId is always extracted from JWT — never from request body.
 */
@RestController
@RequestMapping("/follows")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Follow Service",
     description = "Directed social graph — follow/unfollow, counts, mutual connections, suggestions")
public class FollowResource {

    private final FollowService followService;

    // PROTECTED ENDPOINTS

    @PostMapping("/{followeeId}")
    @Operation(summary = "Follow a user",
               description = "Creates an ACTIVE follow relationship. " +
                             "Self-follow and duplicate follow are rejected. " +
                             "followerId is extracted from JWT.")
    public ResponseEntity<ApiResponseDTO<FollowResponseDTO>> follow(
            @PathVariable Integer followeeId,
            HttpServletRequest httpRequest) {

        Integer followerId = getRequestingUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(followService.follow(followerId, followeeId));
    }

    @DeleteMapping("/{followeeId}")
    @Operation(summary = "Unfollow a user",
               description = "Removes the follow relationship. " +
                             "Throws 404 if not currently following.")
    public ResponseEntity<ApiResponseDTO<String>> unfollow(
            @PathVariable Integer followeeId,
            HttpServletRequest httpRequest) {

        Integer followerId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(followService.unfollow(followerId, followeeId));
    }

    @GetMapping("/check/{followeeId}")
    @Operation(summary = "Check if current user is following another user",
               description = "Returns true/false — used to toggle Follow/Unfollow button state.")
    public ResponseEntity<ApiResponseDTO<Boolean>> isFollowing(
            @PathVariable Integer followeeId,
            HttpServletRequest httpRequest) {

        Integer followerId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(followService.isFollowing(followerId, followeeId));
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get suggested users to follow",
               description = "Returns second-degree connections not already followed. " +
                             "'People You May Know' feature. userId from JWT.")
    public ResponseEntity<ApiResponseDTO<List<Integer>>> getSuggestedUsers(
            HttpServletRequest httpRequest) {

        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(followService.getSuggestedUsers(userId));
    }

    // PUBLIC ENDPOINTS

    @GetMapping("/{userId}/followers")
    @Operation(summary = "Get followers of a user",
               description = "Returns the list of users who follow this user.")
    public ResponseEntity<ApiResponseDTO<List<FollowResponseDTO>>> getFollowers(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(followService.getFollowers(userId));
    }

    @GetMapping("/{userId}/following")
    @Operation(summary = "Get users this user is following",
               description = "Returns the list of users this person follows.")
    public ResponseEntity<ApiResponseDTO<List<FollowResponseDTO>>> getFollowing(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(followService.getFollowing(userId));
    }

    @GetMapping("/{userId}/follower-count")
    @Operation(summary = "Get follower count",
               description = "Returns total number of followers. Used for profile badge.")
    public ResponseEntity<ApiResponseDTO<Integer>> getFollowerCount(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(followService.getFollowerCount(userId));
    }

    @GetMapping("/{userId}/following-count")
    @Operation(summary = "Get following count",
               description = "Returns total number of users this person follows.")
    public ResponseEntity<ApiResponseDTO<Integer>> getFollowingCount(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(followService.getFollowingCount(userId));
    }

    @GetMapping("/{userId}/counts")
    @Operation(summary = "Get both follower and following counts in one call",
               description = "Returns FollowCountDTO with followerCount and followingCount. " +
                             "Used for profile page header — avoids two separate API calls.")
    public ResponseEntity<ApiResponseDTO<FollowCountDTO>> getFollowCounts(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(followService.getFollowCounts(userId));
    }

    @GetMapping("/{userId}/mutual")
    @Operation(summary = "Get mutual connections",
               description = "Returns userIds of users that both follow each other. " +
                             "Used for 'Mutual connections' display and moderation trust scoring.")
    public ResponseEntity<ApiResponseDTO<List<Integer>>> getMutualFollows(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(followService.getMutualFollows(userId));
    }

    @GetMapping("/{userId}/followee-ids")
    @Operation(summary = "Get followee IDs (lightweight — for news feed)",
               description = "Returns just the list of followee userIds. " +
                             "Called by post-service to build the personalised news feed.")
    public ResponseEntity<ApiResponseDTO<List<Integer>>> getFolloweeIds(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(followService.getFolloweeIds(userId));
    }

    // PRIVATE HELPERS

    /**
     * Extract userId from request attribute set by JwtAuthenticationFilter.
     */
    private Integer getRequestingUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("requestingUserId");
        if (userId == null) {
            throw new RuntimeException("Unauthorized: userId not found in request");
        }
        return (Integer) userId;
    }
}