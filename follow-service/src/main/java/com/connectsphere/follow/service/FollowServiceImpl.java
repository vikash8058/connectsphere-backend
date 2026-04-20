package com.connectsphere.follow.service;

import com.connectsphere.follow.client.AuthServiceClient;
import com.connectsphere.follow.dto.*;
import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.exception.AlreadyFollowingException;
import com.connectsphere.follow.exception.FollowNotFoundException;
import com.connectsphere.follow.exception.SelfFollowException;
import com.connectsphere.follow.exception.UserNotFoundException;
import com.connectsphere.follow.message.NotificationEventMessage;
import com.connectsphere.follow.repository.FollowRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * FollowServiceImpl - Business Logic Implementation
 *
 * Key flows:
 * 1. follow()            -> Self-follow check -> Duplicate check -> Save Follow (ACTIVE)
 * 2. unfollow()          -> Find existing follow -> Delete
 * 3. isFollowing()       -> existsByFollowerIdAndFolloweeIdAndStatus(ACTIVE)
 * 4. getFollowers()      -> findByFolloweeIdAndStatus(ACTIVE) -> map to DTOs
 * 5. getFollowing()      -> findByFollowerIdAndStatus(ACTIVE) -> map to DTOs
 * 6. getMutualFollows()  -> DB query finds both-way follows
 * 7. getSuggestedUsers() -> DB query finds second-degree connections
 * 8. getFolloweeIds()    -> Lightweight query returns only IDs (for post-service feed)
 *
 * followerId is NEVER taken from request body — always from JWT token.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final AuthServiceClient authServiceClient;
    // ADD at top of class
    private final RabbitTemplate rabbitTemplate;

    @Value("${notification.rabbitmq.exchange}")
    private String notificationExchange;

    @Value("${notification.rabbitmq.routing-key}")
    private String notificationRoutingKey;

    // FOLLOW

    // Update the follow() method:
    @Override
    @Transactional
    public ApiResponseDTO<FollowResponseDTO> follow(Integer followerId, Integer followeeId) {
        log.info("Follow request — followerId: {}, followeeId: {}", followerId, followeeId);

        // Check 1 — self-follow
        if (followerId.equals(followeeId)) {
            throw new SelfFollowException("You cannot follow yourself");
        }

        // Check 2 — verify followee actually exists in auth-service
        // Extract JWT from the current incoming request and forward it
        String authHeader = getAuthorizationHeader();
        try {
            UserExistsResponseDTO response = authServiceClient.getUserById(followeeId, authHeader);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new UserNotFoundException("User not found with id: " + followeeId);
            }
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Could not verify followee existence (userId={}): {}", followeeId, e.getMessage());
            throw new UserNotFoundException("User not found with id: " + followeeId);
        }

        // Check 3 — duplicate follow
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            throw new AlreadyFollowingException("You are already following this user");
        }

        Follow follow = Follow.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .status(FollowStatus.ACTIVE)
                .build();

        Follow saved = followRepository.save(follow);
        publishFollowNotification(followerId, followeeId);
        log.info("Follow saved with followId: {}", saved.getFollowId());

        return ApiResponseDTO.success("Followed successfully", toDTO(saved));
    }

    // Add this private helper at the bottom of the class
    private String getAuthorizationHeader() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader("Authorization");
        }
        return null;
    }

    // UNFOLLOW

    @Override
    @Transactional
    public ApiResponseDTO<String> unfollow(Integer followerId, Integer followeeId) {
        log.info("Unfollow request — followerId: {}, followeeId: {}", followerId, followeeId);

        // Step 1 — verify followee actually exists
        String authHeader = getAuthorizationHeader();
        try {
            UserExistsResponseDTO response = authServiceClient.getUserById(followeeId, authHeader);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new UserNotFoundException("User not found with id: " + followeeId);
            }
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Could not verify followee existence (userId={}): {}", followeeId, e.getMessage());
            throw new UserNotFoundException("User not found with id: " + followeeId);
        }

        // Step 2 — check if actually following
        followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId)
                .orElseThrow(() -> new FollowNotFoundException("You are not following this user"));

        // Step 3 — delete
        followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        log.info("Unfollow complete — followerId: {}, followeeId: {}", followerId, followeeId);

        return ApiResponseDTO.success("Unfollowed successfully");
    }

    // IS FOLLOWING

    @Override
    public ApiResponseDTO<Boolean> isFollowing(Integer followerId, Integer followeeId) {
        boolean following = followRepository.existsByFollowerIdAndFolloweeIdAndStatus(
                followerId, followeeId, FollowStatus.ACTIVE);
        return ApiResponseDTO.success("isFollowing fetched", following);
    }

    // GET FOLLOWERS

    @Override
    public ApiResponseDTO<List<FollowResponseDTO>> getFollowers(Integer userId) {
        log.debug("Fetching followers for userId: {}", userId);
        List<FollowResponseDTO> followers = followRepository
                .findByFolloweeIdAndStatus(userId, FollowStatus.ACTIVE)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Followers fetched successfully", followers);
    }

    // GET FOLLOWING

    @Override
    public ApiResponseDTO<List<FollowResponseDTO>> getFollowing(Integer userId) {
        log.debug("Fetching following list for userId: {}", userId);
        List<FollowResponseDTO> following = followRepository
                .findByFollowerIdAndStatus(userId, FollowStatus.ACTIVE)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Following list fetched successfully", following);
    }

    // COUNTS

    @Override
    public ApiResponseDTO<Integer> getFollowerCount(Integer userId) {
        int count = followRepository.countByFolloweeIdAndStatus(userId, FollowStatus.ACTIVE);
        return ApiResponseDTO.success("Follower count fetched", count);
    }

    @Override
    public ApiResponseDTO<Integer> getFollowingCount(Integer userId) {
        int count = followRepository.countByFollowerIdAndStatus(userId, FollowStatus.ACTIVE);
        return ApiResponseDTO.success("Following count fetched", count);
    }

    @Override
    public ApiResponseDTO<FollowCountDTO> getFollowCounts(Integer userId) {
        int followerCount  = followRepository.countByFolloweeIdAndStatus(userId, FollowStatus.ACTIVE);
        int followingCount = followRepository.countByFollowerIdAndStatus(userId, FollowStatus.ACTIVE);

        FollowCountDTO counts = FollowCountDTO.builder()
                .userId(userId)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .build();

        return ApiResponseDTO.success("Follow counts fetched", counts);
    }

    // MUTUAL FOLLOWS

    @Override
    public ApiResponseDTO<List<Integer>> getMutualFollows(Integer userId) {
        log.debug("Fetching mutual follows for userId: {}", userId);
        List<Integer> mutuals = followRepository.findMutualFollows(userId);
        return ApiResponseDTO.success("Mutual follows fetched", mutuals);
    }

    // SUGGESTED USERS

    @Override
    public ApiResponseDTO<List<Integer>> getSuggestedUsers(Integer userId) {
        log.debug("Fetching suggested users for userId: {}", userId);
        List<Integer> suggestions = followRepository.findSuggestedUsers(userId);
        return ApiResponseDTO.success("Suggested users fetched", suggestions);
    }

    // FOLLOWEE IDs (for post-service feed)

    @Override
    public ApiResponseDTO<List<Integer>> getFolloweeIds(Integer followerId) {
        log.debug("Fetching followee IDs for followerId: {}", followerId);
        List<Integer> followeeIds = followRepository.findFolloweeIdsByFollowerId(followerId);
        return ApiResponseDTO.success("Followee IDs fetched", followeeIds);
    }

    // PRIVATE HELPERS

    /** Map Follow entity → FollowResponseDTO */
    private FollowResponseDTO toDTO(Follow follow) {
        return FollowResponseDTO.builder()
                .followId(follow.getFollowId())
                .followerId(follow.getFollowerId())
                .followeeId(follow.getFolloweeId())
                .status(follow.getStatus())
                .createdAt(follow.getCreatedAt())
                .build();
    }

    /**
     * Publish FOLLOW notification to RabbitMQ.
     * Recipient = followeeId (person who got followed).
     * Actor     = followerId (person who followed).
     */
    private void publishFollowNotification(Integer followerId, Integer followeeId) {
        try {
            NotificationEventMessage message = NotificationEventMessage.builder()
                    .recipientId(followeeId)
                    .actorId(followerId)
                    .type("FOLLOW")
                    .message("Someone started following you")
                    .deepLinkUrl("/profile/" + followerId)
                    .build();

            rabbitTemplate.convertAndSend(notificationExchange, notificationRoutingKey, message);
            log.debug("FOLLOW notification published for followeeId: {}", followeeId);
        } catch (Exception e) {
            log.warn("Failed to publish FOLLOW notification: {}", e.getMessage());
        }
    }
}