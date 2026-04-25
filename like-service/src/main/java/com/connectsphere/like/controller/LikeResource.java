package com.connectsphere.like.controller;

import com.connectsphere.like.dto.*;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LikeResource - REST Controller for ConnectSphere Like Service
 *
 * Base path: /api/v1/likes (via context-path in application.yml)
 *
 * All endpoints require JWT (like-service has no public read endpoints).
 * userId is always read from JWT attribute — never from the request body.
 *
 * ENDPOINTS:
 *   POST   /likes                                    - React to post/comment
 *   DELETE /likes?targetId=&targetType=              - Remove reaction
 *   PUT    /likes/change                             - Change reaction type
 *   GET    /likes/has?targetId=&targetType=          - Check if user reacted
 *   GET    /likes/target?targetId=&targetType=       - All reactions on a target
 *   GET    /likes/user/{userId}                      - All reactions by a user
 *   GET    /likes/count?targetId=&targetType=        - Total count on a target
 *   GET    /likes/count/type?targetId=&targetType=&reactionType= - Count by type
 *   GET    /likes/summary?targetId=&targetType=      - Full emoji reaction bar
 */
@RestController
@RequestMapping("/likes")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Like Service", description = "Polymorphic reactions on posts and comments")
public class LikeResource {

    private final LikeService likeService;

    @PostMapping
    @Operation(summary = "React to a post or comment",
               description = "Adds a reaction (LIKE/LOVE/HAHA/WOW/SAD/ANGRY). " +
                             "One reaction per user per target enforced. userId from JWT.")
    public ResponseEntity<ApiResponseDTO<LikeResponseDTO>> likeTarget(
            @Valid @RequestBody LikeRequestDTO request,
            HttpServletRequest httpRequest) {

        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(likeService.likeTarget(userId, request));
    }

    @DeleteMapping
    @Operation(summary = "Remove a reaction (unlike)",
               description = "Removes the user's existing reaction from a post/comment.")
    public ResponseEntity<ApiResponseDTO<String>> unlikeTarget(
            @RequestParam Integer targetId,
            @RequestParam TargetType targetType,
            HttpServletRequest httpRequest) {

        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(likeService.unlikeTarget(userId, targetId, targetType));
    }

    @PutMapping("/change")
    @Operation(summary = "Change reaction type",
               description = "Updates an existing reaction to a different type (e.g., LIKE → LOVE). " +
                             "User must have already reacted to the target.")
    public ResponseEntity<ApiResponseDTO<LikeResponseDTO>> changeReaction(
            @Valid @RequestBody ChangeReactionRequestDTO request,
            HttpServletRequest httpRequest) {

        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(likeService.changeReaction(userId, request));
    }

    @GetMapping("/my")
    @Operation(summary = "Get the current user's specific reaction on a target",
               description = "Returns the reaction details (type, timestamp) if the user has reacted. " +
                             "Used to show LIKE vs LOVE vs HAHA state on the button.")
    public ResponseEntity<ApiResponseDTO<LikeResponseDTO>> getMyReaction(
            @RequestParam Integer targetId,
            @RequestParam TargetType targetType,
            HttpServletRequest httpRequest) {

        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(likeService.getUserReaction(userId, targetId, targetType));
    }

    @GetMapping("/has")
    @Operation(summary = "Check if the current user has reacted",
               description = "Returns true/false — used to toggle like button state in UI.")
    public ResponseEntity<ApiResponseDTO<Boolean>> hasLiked(
            @RequestParam Integer targetId,
            @RequestParam TargetType targetType,
            HttpServletRequest httpRequest) {

        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(likeService.hasLiked(userId, targetId, targetType));
    }

    @GetMapping("/target")
    @Operation(summary = "Get all reactions on a post or comment",
               description = "Returns list of all reactions on the given target.")
    public ResponseEntity<ApiResponseDTO<List<LikeResponseDTO>>> getLikesByTarget(
            @RequestParam Integer targetId,
            @RequestParam TargetType targetType) {

        return ResponseEntity.ok(likeService.getLikesByTarget(targetId, targetType));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all reactions made by a user",
               description = "Returns all reactions (across posts and comments) made by this user.")
    public ResponseEntity<ApiResponseDTO<List<LikeResponseDTO>>> getLikesByUser(
            @PathVariable Integer userId) {

        return ResponseEntity.ok(likeService.getLikesByUser(userId));
    }

    @GetMapping("/count")
    @Operation(summary = "Get total reaction count on a target",
               description = "Returns total count of all reaction types combined.")
    public ResponseEntity<ApiResponseDTO<Integer>> getLikeCount(
            @RequestParam Integer targetId,
            @RequestParam TargetType targetType) {

        return ResponseEntity.ok(likeService.getLikeCount(targetId, targetType));
    }

    @GetMapping("/count/type")
    @Operation(summary = "Get count of a specific reaction type",
               description = "Returns count of a single reaction type (e.g., LOVE count on a post).")
    public ResponseEntity<ApiResponseDTO<Integer>> getLikeCountByType(
            @RequestParam Integer targetId,
            @RequestParam TargetType targetType,
            @RequestParam String reactionType) {

        return ResponseEntity.ok(likeService.getLikeCountByType(targetId, targetType, reactionType));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get full emoji reaction summary",
               description = "Returns a map of all 6 reaction types with their counts — " +
                             "used to render the emoji reaction bar on posts/comments.")
    public ResponseEntity<ApiResponseDTO<ReactionSummaryDTO>> getReactionSummary(
            @RequestParam Integer targetId,
            @RequestParam TargetType targetType) {

        return ResponseEntity.ok(likeService.getReactionSummary(targetId, targetType));
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
        if (userId instanceof Number) {
            return ((Number) userId).intValue();
        } else if (userId instanceof String) {
            return Integer.valueOf((String) userId);
        }
        throw new RuntimeException("Unauthorized: userId type mismatch: " + userId.getClass().getName());
    }
}