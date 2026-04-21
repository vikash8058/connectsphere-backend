package com.connectsphere.post.service;

import com.connectsphere.post.dto.*;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import com.connectsphere.post.exception.PostNotFoundException;
import com.connectsphere.post.exception.UnauthorizedActionException;
import com.connectsphere.post.messaging.PostEventPublisher;
import com.connectsphere.post.repository.PostRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PostServiceImpl - Business Logic Implementation
 *
 * Key flows:
 * 1. createPost()      -> Build Post entity from DTO + authorId from JWT
 *                      -> Save to DB -> Return PostResponseDTO
 * 2. getPostById()     -> Fetch by postId (non-deleted) -> Return DTO
 * 3. getFeedForUser()  -> Fetch posts WHERE authorId IN (followeeIds)
 *                      -> Ordered by createdAt DESC
 * 4. deletePost()      -> Ownership check -> softDeleteByPostId()
 * 5. incrementLikes()  -> Atomic UPDATE (no full entity load)
 *
 * authorId is NEVER taken from request body - always from JWT token
 * (extracted in JwtAuthenticationFilter and passed as method parameter by controller).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostEventPublisher eventPublisher;

    // CREATE POST

    @Override
    @Transactional
    public ApiResponseDTO<PostResponseDTO> createPost(Integer authorId, CreatePostRequestDTO request) {
        log.info("Creating post for authorId: {}", authorId);

        // Determine postType automatically if not explicitly set
        PostType postType = request.getPostType();
        if (postType == null) {
            postType = (request.getMediaUrls() == null || request.getMediaUrls().isEmpty())
                    ? PostType.TEXT
                    : PostType.IMAGE;
        }

        Post post = Post.builder()
                .authorId(authorId)
                .content(request.getContent())
                .mediaUrls(request.getMediaUrls() != null ? request.getMediaUrls() : List.of())
                .postType(postType)
                .visibility(request.getVisibility() != null ? request.getVisibility() : Visibility.PUBLIC)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .isDeleted(false)
                .build();

        Post saved = postRepository.save(post);
        log.info("Post created with postId: {}", saved.getPostId());

        // Publish event for search-service to index hashtags
        eventPublisher.publishPostCreated(saved);

        return ApiResponseDTO.success("Post created successfully", toDTO(saved));
    }

    // READ OPERATIONS

    @Override
    public ApiResponseDTO<PostResponseDTO> getPostById(Integer postId) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException(
                        "Post not found with id: " + postId));
        return ApiResponseDTO.success("Post fetched successfully", toDTO(post));
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getPostsByUser(Integer authorId) {
        log.debug("Fetching posts for authorId: {}", authorId);
        List<PostResponseDTO> posts = postRepository
                .findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Posts fetched successfully", posts);
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getFeedForUser(List<Integer> followeeIds) {
        log.debug("Building feed for followeeIds count: {}", followeeIds.size());

        if (followeeIds == null || followeeIds.isEmpty()) {
            // Empty feed — user follows nobody yet
            return ApiResponseDTO.success("Feed is empty", List.of());
        }

        List<PostResponseDTO> feed = postRepository.findFeedByUserIds(followeeIds)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ApiResponseDTO.success("Feed fetched successfully", feed);
    }

    // UPDATE POST

    @Override
    @Transactional
    public ApiResponseDTO<PostResponseDTO> updatePost(Integer postId, Integer requestingUserId,
                                                       UpdatePostRequestDTO request) {
        log.info("Update request for postId: {} by userId: {}", postId, requestingUserId);

        Post post = findActivePostById(postId);
        checkOwnership(post, requestingUserId);

        // Store old content for re-indexing
        String oldContent = post.getContent();

        if (request.getContent() != null && !request.getContent().isBlank()) {
            post.setContent(request.getContent());
        }

        Post updated = postRepository.save(post);
        log.info("Post updated: {}", postId);

        // Publish event for search-service to re-index hashtags
        eventPublisher.publishPostUpdated(Post.builder()
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .content(oldContent)
                .visibility(post.getVisibility())
                .build(), updated);

        return ApiResponseDTO.success("Post updated successfully", toDTO(updated));
    }

    // DELETE POST

    @Override
    @Transactional
    public ApiResponseDTO<String> deletePost(Integer postId, Integer requestingUserId,
                                              String requestingUserRole) {
        log.info("Delete request for postId: {} by userId: {}", postId, requestingUserId);

        Post post = findActivePostById(postId);

        // Admin can delete any post; regular users can only delete their own
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requestingUserRole)
                || "MODERATOR".equalsIgnoreCase(requestingUserRole);

        if (!isAdmin) {
            checkOwnership(post, requestingUserId);
        }

        postRepository.softDeleteByPostId(postId);
        log.info("Post soft-deleted: {}", postId);

        // Publish event for search-service to remove index
        eventPublisher.publishPostDeleted(postId, post.getAuthorId());

        return ApiResponseDTO.success("Post deleted successfully");
    }

    // SEARCH

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> searchPosts(String keyword) {
        log.debug("Searching posts with keyword: {}", keyword);
        List<PostResponseDTO> results = postRepository.searchByContent(keyword)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Search results fetched", results);
    }

    // COUNTER OPERATIONS (called by other microservices)

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementLikes(Integer postId) {
        ensurePostExists(postId);
        postRepository.incrementLikes(postId);
        log.debug("Likes incremented for postId: {}", postId);
        return ApiResponseDTO.success("Likes count incremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> decrementLikes(Integer postId) {
        ensurePostExists(postId);
        postRepository.decrementLikes(postId);
        log.debug("Likes decremented for postId: {}", postId);
        return ApiResponseDTO.success("Likes count decremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementComments(Integer postId) {
        ensurePostExists(postId);
        postRepository.incrementComments(postId);
        log.debug("Comments incremented for postId: {}", postId);
        return ApiResponseDTO.success("Comments count incremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> decrementComments(Integer postId) {
        ensurePostExists(postId);
        postRepository.decrementComments(postId);
        log.debug("Comments decremented for postId: {}", postId);
        return ApiResponseDTO.success("Comments count decremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementShares(Integer postId) {
        ensurePostExists(postId);
        postRepository.incrementShares(postId);
        log.debug("Shares incremented for postId: {}", postId);
        return ApiResponseDTO.success("Shares count incremented");
    }

    // CHANGE VISIBILITY

    @Override
    @Transactional
    public ApiResponseDTO<PostResponseDTO> changeVisibility(Integer postId, Integer requestingUserId,
                                                              String visibilityStr) {
        log.info("Visibility change for postId: {} to: {}", postId, visibilityStr);

        Post post = findActivePostById(postId);
        checkOwnership(post, requestingUserId);

        Visibility newVisibility;
        try {
            newVisibility = Visibility.valueOf(visibilityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid visibility value: " + visibilityStr +
                    ". Allowed: PUBLIC, FOLLOWERS_ONLY, PRIVATE");
        }

        postRepository.updateVisibility(postId, newVisibility);
        post.setVisibility(newVisibility);
        log.info("Visibility updated for postId: {}", postId);
        return ApiResponseDTO.success("Visibility updated successfully", toDTO(post));
    }

    // POST COUNT

    @Override
    public ApiResponseDTO<Integer> getPostCount(Integer authorId) {
        int count = postRepository.countByAuthorIdAndIsDeletedFalse(authorId);
        return ApiResponseDTO.success("Post count fetched", count);
    }

    // PRIVATE HELPERS

    /**
     * Fetch active (not deleted) post by ID — throws if not found.
     */
    private Post findActivePostById(Integer postId) {
        return postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException(
                        "Post not found with id: " + postId));
    }

    /**
     * Check that the requesting user is the owner of the post.
     * Throws UnauthorizedActionException if they are not.
     */
    private void checkOwnership(Post post, Integer requestingUserId) {
        if (!post.getAuthorId().equals(requestingUserId)) {
            throw new UnauthorizedActionException(
                    "You are not authorized to modify this post");
        }
    }

    // PUBLIC FEED
    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getPublicFeed() {
        log.debug("Fetching public feed");
        List<PostResponseDTO> posts = postRepository
                .findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(Visibility.PUBLIC)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Public feed fetched successfully", posts);
    }

    /**
     * Verify post exists (for counter increment/decrement operations).
     * Throws PostNotFoundException if it doesn't exist or is deleted.
     */
    private void ensurePostExists(Integer postId) {
        if (!postRepository.findByPostIdAndIsDeletedFalse(postId).isPresent()) {
            throw new PostNotFoundException("Post not found with id: " + postId);
        }
    }

    /**
     * Map Post entity → PostResponseDTO.
     * Never expose isDeleted in response.
     */
    private PostResponseDTO toDTO(Post post) {
        return PostResponseDTO.builder()
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .content(post.getContent())
                .mediaUrls(post.getMediaUrls())
                .postType(post.getPostType())
                .visibility(post.getVisibility())
                .likesCount(post.getLikesCount())
                .commentsCount(post.getCommentsCount())
                .sharesCount(post.getSharesCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}