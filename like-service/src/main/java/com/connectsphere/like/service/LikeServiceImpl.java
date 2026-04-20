package com.connectsphere.like.service;

import com.connectsphere.like.dto.*;
import com.connectsphere.like.entity.Like;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.exception.AlreadyLikedException;
import com.connectsphere.like.exception.LikeNotFoundException;
import com.connectsphere.like.client.CommentServiceClient;
import com.connectsphere.like.client.PostServiceClient;
import com.connectsphere.like.repository.LikeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LikeServiceImpl - Business Logic Implementation
 *
 * Inter-service calls now use Feign clients instead of RestTemplate:
 *   PostServiceClient    → increment/decrement post likesCount
 *   CommentServiceClient → increment/decrement comment likesCount
 *
 * Key flows:
 * 1. likeTarget()     -> Duplicate check -> Save Like
 *                     -> PostServiceClient.incrementLikeCount()    (if POST)
 *                     -> CommentServiceClient.incrementLikeCount() (if COMMENT)
 *
 * 2. unlikeTarget()   -> Find existing reaction -> Delete
 *                     -> PostServiceClient.decrementLikeCount()    (if POST)
 *                     -> CommentServiceClient.decrementLikeCount() (if COMMENT)
 *
 * 3. changeReaction() -> Delete old -> Insert new (atomic @Transactional)
 *                     -> No counter change (still 1 reaction total)
 *
 * 4. getReactionSummary() -> Build map of all 6 reaction types + counts
 *
 * userId is NEVER taken from request body — always from JWT token.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final LikeRepository likeRepository;
    private final PostServiceClient postServiceClient;       // Feign → post-service
    private final CommentServiceClient commentServiceClient; // Feign → comment-service

    // LIKE TARGET

    @Override
    @Transactional
    public ApiResponseDTO<LikeResponseDTO> likeTarget(Integer userId, LikeRequestDTO request) {
        log.info("Like request — userId: {}, targetId: {}, targetType: {}, reactionType: {}",
                userId, request.getTargetId(), request.getTargetType(), request.getReactionType());

        // One reaction per user per target — check before insert
        boolean alreadyLiked = likeRepository.existsByUserIdAndTargetIdAndTargetType(
                userId, request.getTargetId(), request.getTargetType());

        if (alreadyLiked) {
            throw new AlreadyLikedException(
                    "You have already reacted to this "
                    + request.getTargetType().name().toLowerCase()
                    + ". Use changeReaction to update it.");
        }

        Like like = Like.builder()
                .userId(userId)
                .targetId(request.getTargetId())
                .targetType(request.getTargetType())
                .reactionType(request.getReactionType() != null
                        ? request.getReactionType()
                        : ReactionType.LIKE)
                .build();

        Like saved = likeRepository.save(like);
        log.info("Like saved with likeId: {}", saved.getLikeId());

        // Notify target service to increment its likesCount counter via Feign
        syncCounterIncrement(request.getTargetType(), request.getTargetId());

        return ApiResponseDTO.success("Reaction added successfully", toDTO(saved));
    }

    // UNLIKE TARGET

    @Override
    @Transactional
    public ApiResponseDTO<String> unlikeTarget(Integer userId, Integer targetId,
                                                TargetType targetType) {
        log.info("Unlike request — userId: {}, targetId: {}, targetType: {}",
                userId, targetId, targetType);

        likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType)
                .orElseThrow(() -> new LikeNotFoundException(
                        "No reaction found for this " + targetType.name().toLowerCase()));

        likeRepository.deleteByUserIdAndTargetIdAndTargetType(userId, targetId, targetType);
        log.info("Like removed for userId: {}, targetId: {}", userId, targetId);

        // Notify target service to decrement its likesCount counter via Feign
        syncCounterDecrement(targetType, targetId);

        return ApiResponseDTO.success("Reaction removed successfully");
    }

    // HAS LIKED

    @Override
    public ApiResponseDTO<Boolean> hasLiked(Integer userId, Integer targetId,
                                             TargetType targetType) {
        boolean liked = likeRepository.existsByUserIdAndTargetIdAndTargetType(
                userId, targetId, targetType);
        return ApiResponseDTO.success("hasLiked fetched", liked);
    }

    // GET LIKES BY TARGET

    @Override
    public ApiResponseDTO<List<LikeResponseDTO>> getLikesByTarget(Integer targetId,
                                                                    TargetType targetType) {
        log.debug("Fetching likes for targetId: {}, targetType: {}", targetId, targetType);
        List<LikeResponseDTO> likes = likeRepository
                .findByTargetIdAndTargetType(targetId, targetType)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Likes fetched successfully", likes);
    }

    // GET LIKES BY USER

    @Override
    public ApiResponseDTO<List<LikeResponseDTO>> getLikesByUser(Integer userId) {
        log.debug("Fetching all reactions by userId: {}", userId);
        List<LikeResponseDTO> likes = likeRepository.findByUserId(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("User reactions fetched", likes);
    }

    // GET LIKE COUNT

    @Override
    public ApiResponseDTO<Integer> getLikeCount(Integer targetId, TargetType targetType) {
        int count = likeRepository.countByTargetIdAndTargetType(targetId, targetType);
        return ApiResponseDTO.success("Like count fetched", count);
    }

    // GET LIKE COUNT BY TYPE

    @Override
    public ApiResponseDTO<Integer> getLikeCountByType(Integer targetId, TargetType targetType,
                                                        String reactionType) {
        ReactionType type;
        try {
            type = ReactionType.valueOf(reactionType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid reactionType: " + reactionType
                    + ". Allowed: LIKE, LOVE, HAHA, WOW, SAD, ANGRY");
        }
        int count = likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                targetId, targetType, type);
        return ApiResponseDTO.success("Count by reaction type fetched", count);
    }

    // GET REACTION SUMMARY (emoji bar)

    @Override
    public ApiResponseDTO<ReactionSummaryDTO> getReactionSummary(Integer targetId,
                                                                   TargetType targetType) {
        log.debug("Building reaction summary for targetId: {}, targetType: {}",
                targetId, targetType);

        // Build map for all 6 types in fixed display order
        Map<String, Integer> reactions = new LinkedHashMap<>();
        for (ReactionType type : ReactionType.values()) {
            int count = likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    targetId, targetType, type);
            reactions.put(type.name(), count);
        }

        int total = reactions.values().stream().mapToInt(Integer::intValue).sum();

        ReactionSummaryDTO summary = ReactionSummaryDTO.builder()
                .targetId(targetId)
                .targetType(targetType.name())
                .totalCount(total)
                .reactions(reactions)
                .build();

        return ApiResponseDTO.success("Reaction summary fetched", summary);
    }

    // CHANGE REACTION

    @Override
    @Transactional
    public ApiResponseDTO<LikeResponseDTO> changeReaction(Integer userId,
                                                           ChangeReactionRequestDTO request) {
        log.info("Change reaction — userId: {}, targetId: {}, newType: {}",
                userId, request.getTargetId(), request.getNewReactionType());

        // Must have an existing reaction to change
        likeRepository.findByUserIdAndTargetIdAndTargetType(
                        userId, request.getTargetId(), request.getTargetType())
                .orElseThrow(() -> new LikeNotFoundException(
                        "No existing reaction found. Use POST /likes to react first."));

        // Atomic: delete old, insert new — counter stays the same (still 1 reaction)
        likeRepository.deleteByUserIdAndTargetIdAndTargetType(
                userId, request.getTargetId(), request.getTargetType());

        Like newLike = Like.builder()
                .userId(userId)
                .targetId(request.getTargetId())
                .targetType(request.getTargetType())
                .reactionType(request.getNewReactionType())
                .build();

        Like saved = likeRepository.save(newLike);
        log.info("Reaction changed to {} for likeId: {}",
                request.getNewReactionType(), saved.getLikeId());

        return ApiResponseDTO.success("Reaction changed successfully", toDTO(saved));
    }

    // PRIVATE HELPERS

    /**
     * Increment likesCount on the target service via Feign after a successful like.
     * Failures are logged but do NOT roll back the like — eventual consistency.
     */
    private void syncCounterIncrement(TargetType targetType, Integer targetId) {
        try {
            if (targetType == TargetType.POST) {
                postServiceClient.incrementLikeCount(targetId);
                log.debug("post-service likesCount incremented for postId: {}", targetId);
            } else if (targetType == TargetType.COMMENT) {
                commentServiceClient.incrementLikeCount(targetId);
                log.debug("comment-service likesCount incremented for commentId: {}", targetId);
            }
        } catch (Exception e) {
            // Graceful degradation — counter is a denormalised cache, not source of truth
            log.warn("Failed to sync increment counter for {} {}: {}",
                    targetType, targetId, e.getMessage());
        }
    }

    /**
     * Decrement likesCount on the target service via Feign after an unlike.
     */
    private void syncCounterDecrement(TargetType targetType, Integer targetId) {
        try {
            if (targetType == TargetType.POST) {
                postServiceClient.decrementLikeCount(targetId);
                log.debug("post-service likesCount decremented for postId: {}", targetId);
            } else if (targetType == TargetType.COMMENT) {
                commentServiceClient.decrementLikeCount(targetId);
                log.debug("comment-service likesCount decremented for commentId: {}", targetId);
            }
        } catch (Exception e) {
            log.warn("Failed to sync decrement counter for {} {}: {}",
                    targetType, targetId, e.getMessage());
        }
    }

    /** Map Like entity → LikeResponseDTO */
    private LikeResponseDTO toDTO(Like like) {
        return LikeResponseDTO.builder()
                .likeId(like.getLikeId())
                .userId(like.getUserId())
                .targetId(like.getTargetId())
                .targetType(like.getTargetType())
                .reactionType(like.getReactionType())
                .createdAt(like.getCreatedAt())
                .build();
    }
}