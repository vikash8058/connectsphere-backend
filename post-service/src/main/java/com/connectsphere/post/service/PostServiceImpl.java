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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostEventPublisher eventPublisher;
    private final com.connectsphere.post.client.FollowServiceClient followServiceClient;

    @Override
    @Transactional
    public ApiResponseDTO<PostResponseDTO> createPost(Integer authorId, CreatePostRequestDTO request) {
        PostType type = request.getPostType();
        if (type == null) {
            type = (request.getMediaUrls() == null || request.getMediaUrls().isEmpty()) ? PostType.TEXT : PostType.IMAGE;
        }

        Post post = Post.builder()
                .authorId(authorId)
                .content(request.getContent())
                .mediaUrls(request.getMediaUrls() != null ? new ArrayList<>(request.getMediaUrls()) : new ArrayList<>())
                .postType(type)
                .visibility(request.getVisibility() != null ? request.getVisibility() : Visibility.PUBLIC)
                .isElite(request.getIsElite() != null ? request.getIsElite() : false)
                .build();

        Post saved = postRepository.save(post);
        eventPublisher.publishPostCreated(saved);
        return ApiResponseDTO.success("Post created successfully", toDTO(saved));
    }

    // For simplicity, we assume all posts are visible to everyone. In a real app, we'd check the post's visibility and the relationship between users.
    @Override
    public ApiResponseDTO<PostResponseDTO> getPostById(Integer postId) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        return ApiResponseDTO.success("Post fetched successfully", toDTO(post));
    }

    // In a real app, we'd also check the post's visibility and the relationship between users to filter out posts that shouldn't be visible to the requesting user.
    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getPostsByUser(Integer authorId, Integer requestingUserId, String authHeader) {
        List<Post> posts = postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId);
        return ApiResponseDTO.success("Posts fetched successfully", posts.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getFeedForUser(Integer requestingUserId, String authHeader) {
        if (requestingUserId == null) {
            return ApiResponseDTO.success("Feed fetched successfully", new ArrayList<>());
        }
        List<Integer> followeeIds = new ArrayList<>();
        followeeIds.add(requestingUserId);
        try {
            ApiResponseDTO<List<Integer>> res = followServiceClient.getFolloweeIds(requestingUserId, authHeader);
            if (res != null && res.getData() != null) followeeIds.addAll(res.getData());
        } catch (Exception e) { log.error("Follow service error", e); }

        List<PostResponseDTO> feed = postRepository.findFeedPersonalized(followeeIds, requestingUserId)
                .stream().map(this::toDTO).collect(Collectors.toList());
        return ApiResponseDTO.success("Feed fetched successfully", feed);
    }

    @Override
    @Transactional
    public ApiResponseDTO<PostResponseDTO> updatePost(Integer postId, Integer requestingUserId, UpdatePostRequestDTO request) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        if (!post.getAuthorId().equals(requestingUserId)) throw new UnauthorizedActionException("You are not authorized to perform this action");
        if (request.getContent() != null) post.setContent(request.getContent());
        return ApiResponseDTO.success("Post updated successfully", toDTO(postRepository.save(post)));
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> deletePost(Integer postId, Integer requestingUserId, String role) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        if (!"ADMIN".equals(role) && !"MODERATOR".equals(role) && !post.getAuthorId().equals(requestingUserId))
            throw new UnauthorizedActionException("You are not authorized to perform this action");
        postRepository.softDeleteByPostId(postId);
        eventPublisher.publishPostDeleted(postId, post.getAuthorId());
        return ApiResponseDTO.success("Post deleted successfully");
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> searchPosts(String keyword) {
        return ApiResponseDTO.success("Search results fetched successfully", postRepository.searchByContent(keyword)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementLikes(Integer postId) {
        postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        postRepository.incrementLikes(postId);
        return ApiResponseDTO.success("Likes incremented successfully");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> decrementLikes(Integer postId) {
        postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        postRepository.decrementLikes(postId);
        return ApiResponseDTO.success("Likes decremented successfully");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementComments(Integer postId) {
        postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        postRepository.incrementComments(postId);
        return ApiResponseDTO.success("Comments incremented successfully");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> decrementComments(Integer postId) {
        postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        postRepository.decrementComments(postId);
        return ApiResponseDTO.success("Comments decremented successfully");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementShares(Integer postId) {
        postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        postRepository.incrementShares(postId);
        return ApiResponseDTO.success("Shares incremented successfully");
    }

    @Override
    @Transactional
    public ApiResponseDTO<PostResponseDTO> changeVisibility(Integer postId, Integer requestingUserId, String visibility) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        if (!post.getAuthorId().equals(requestingUserId))
            throw new UnauthorizedActionException("You are not authorized to perform this action");
        
        Visibility vis;
        try {
            vis = Visibility.valueOf(visibility.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid visibility value: " + visibility);
        }

        postRepository.updateVisibility(postId, vis);
        post.setVisibility(vis);
        return ApiResponseDTO.success("Visibility updated successfully", toDTO(post));
    }

    @Override
    public ApiResponseDTO<Integer> getPostCount(Integer authorId) {
        return ApiResponseDTO.success("Post count fetched successfully", postRepository.countByAuthorIdAndIsDeletedFalse(authorId));
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getPublicFeed() {
        return ApiResponseDTO.success("Public feed fetched successfully", postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(Visibility.PUBLIC).stream().map(this::toDTO).collect(Collectors.toList()));
    }

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
                .isElite(post.getIsElite())
                .build();
    }
}