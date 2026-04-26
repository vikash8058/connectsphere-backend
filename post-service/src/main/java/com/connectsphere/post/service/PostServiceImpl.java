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
        return ApiResponseDTO.success("Post created", toDTO(saved));
    }

    @Override
    public ApiResponseDTO<PostResponseDTO> getPostById(Integer postId) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Not found"));
        return ApiResponseDTO.success("Fetched", toDTO(post));
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getPostsByUser(Integer authorId, Integer requestingUserId, String authHeader) {
        List<Post> posts = postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId);
        return ApiResponseDTO.success("Fetched", posts.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getFeedForUser(Integer requestingUserId, String authHeader) {
        List<Integer> followeeIds = new ArrayList<>();
        followeeIds.add(requestingUserId);
        try {
            ApiResponseDTO<List<Integer>> res = followServiceClient.getFolloweeIds(requestingUserId, authHeader);
            if (res != null && res.getData() != null) followeeIds.addAll(res.getData());
        } catch (Exception e) { log.error("Follow service error", e); }

        List<PostResponseDTO> feed = postRepository.findFeedPersonalized(followeeIds, requestingUserId)
                .stream().map(this::toDTO).collect(Collectors.toList());
        return ApiResponseDTO.success("Feed fetched", feed);
    }

    @Override
    @Transactional
    public ApiResponseDTO<PostResponseDTO> updatePost(Integer postId, Integer requestingUserId, UpdatePostRequestDTO request) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Not found"));
        if (!post.getAuthorId().equals(requestingUserId)) throw new UnauthorizedActionException("Unauthorized");
        if (request.getContent() != null) post.setContent(request.getContent());
        return ApiResponseDTO.success("Updated", toDTO(postRepository.save(post)));
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> deletePost(Integer postId, Integer requestingUserId, String role) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Not found"));
        if (!"ADMIN".equals(role) && !post.getAuthorId().equals(requestingUserId)) throw new UnauthorizedActionException("Unauthorized");
        postRepository.softDeleteByPostId(postId);
        eventPublisher.publishPostDeleted(postId, post.getAuthorId());
        return ApiResponseDTO.success("Deleted");
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> searchPosts(String keyword) {
        return ApiResponseDTO.success("Results", postRepository.searchByContent(keyword).stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementLikes(Integer postId) {
        postRepository.incrementLikes(postId);
        return ApiResponseDTO.success("Incremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> decrementLikes(Integer postId) {
        postRepository.decrementLikes(postId);
        return ApiResponseDTO.success("Decremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementComments(Integer postId) {
        postRepository.incrementComments(postId);
        return ApiResponseDTO.success("Incremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> decrementComments(Integer postId) {
        postRepository.decrementComments(postId);
        return ApiResponseDTO.success("Decremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> incrementShares(Integer postId) {
        postRepository.incrementShares(postId);
        return ApiResponseDTO.success("Incremented");
    }

    @Override
    @Transactional
    public ApiResponseDTO<PostResponseDTO> changeVisibility(Integer postId, Integer requestingUserId, String visibility) {
        Post post = postRepository.findByPostIdAndIsDeletedFalse(postId).orElseThrow(() -> new PostNotFoundException("Not found"));
        if (!post.getAuthorId().equals(requestingUserId)) throw new UnauthorizedActionException("Unauthorized");
        post.setVisibility(Visibility.valueOf(visibility.toUpperCase()));
        return ApiResponseDTO.success("Updated", toDTO(postRepository.save(post)));
    }

    @Override
    public ApiResponseDTO<Integer> getPostCount(Integer authorId) {
        return ApiResponseDTO.success("Count", postRepository.countByAuthorIdAndIsDeletedFalse(authorId));
    }

    @Override
    public ApiResponseDTO<List<PostResponseDTO>> getPublicFeed() {
        return ApiResponseDTO.success("Public feed", postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(Visibility.PUBLIC).stream().map(this::toDTO).collect(Collectors.toList()));
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