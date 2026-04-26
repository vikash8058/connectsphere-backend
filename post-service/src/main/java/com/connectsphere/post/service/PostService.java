package com.connectsphere.post.service;

import com.connectsphere.post.dto.*;
import com.connectsphere.post.entity.Post;

import java.util.List;

/**
 * PostService - Business Contract (Interface)
 *
 * Declares all operations as per ConnectSphere case study section 4.2:
 *   createPost(), getPostById(), getPostsByUser(), getFeedForUser(),
 *   updatePost(), deletePost(), searchPosts(), incrementLikes(),
 *   decrementLikes(), incrementComments(), decrementComments(),
 *   incrementShares(), changeVisibility(), getPostCount()
 *
 * Separation of interface and implementation follows the same pattern
 * as AuthService / AuthServiceImpl in auth-service.
 */
public interface PostService {

    /**
     * Create a new post for the authenticated user.
     * authorId is extracted from the JWT token (not from request body).
     */
    ApiResponseDTO<PostResponseDTO> createPost(Integer authorId, CreatePostRequestDTO request);

    /**
     * Get a single post by its ID.
     * Respects visibility: PRIVATE posts only returned to their author.
     * FOLLOWERS_ONLY posts only returned to followers (enforced by caller).
     */
    ApiResponseDTO<PostResponseDTO> getPostById(Integer postId);

    /**
     * Get all posts by a specific user (their public timeline).
     * Only PUBLIC posts returned for non-authors.
     */
    ApiResponseDTO<List<PostResponseDTO>> getPostsByUser(Integer authorId, Integer requestingUserId, String authHeader);

    /**
     * Personalised news feed — posts from followed users.
     * Automatically fetches followee IDs using the requestingUserId.
     */
    ApiResponseDTO<List<PostResponseDTO>> getFeedForUser(Integer requestingUserId, String authHeader);

    /**
     * Update the text content of a post.
     * Only the post author can update their own post.
     */
    ApiResponseDTO<PostResponseDTO> updatePost(Integer postId, Integer requestingUserId,
                                               UpdatePostRequestDTO request);

    /**
     * Soft-delete a post (isDeleted = true).
     * Only the post author or an ADMIN can delete a post.
     */
    ApiResponseDTO<String> deletePost(Integer postId, Integer requestingUserId, String requestingUserRole);

    /**
     * Full-text search in post content (PUBLIC posts only).
     * Case-insensitive LIKE query on the content column.
     */
    ApiResponseDTO<List<PostResponseDTO>> searchPosts(String keyword);

    /**
     * Increment likesCount by 1 — called by like-service.
     */
    ApiResponseDTO<String> incrementLikes(Integer postId);

    /**
     * Decrement likesCount by 1 — called by like-service on unlike.
     */
    ApiResponseDTO<String> decrementLikes(Integer postId);

    /**
     * Increment commentsCount by 1 — called by comment-service on addComment().
     */
    ApiResponseDTO<String> incrementComments(Integer postId);

    /**
     * Decrement commentsCount by 1 — called by comment-service on deleteComment().
     */
    ApiResponseDTO<String> decrementComments(Integer postId);

    /**
     * Increment sharesCount by 1 — called when a user reposts.
     */
    ApiResponseDTO<String> incrementShares(Integer postId);

    /**
     * Change post visibility — PUBLIC / FOLLOWERS_ONLY / PRIVATE.
     * Only the post author can change visibility.
     */
    ApiResponseDTO<PostResponseDTO> changeVisibility(Integer postId, Integer requestingUserId,
                                                      String visibility);

    /**
     * Get total non-deleted post count for a user.
     * Displayed as the post count badge on profile page.
     */
    ApiResponseDTO<Integer> getPostCount(Integer authorId);
    /**
     * Get all PUBLIC posts ordered by newest first.
     * Used for guest browsing — no login needed.
     */
    ApiResponseDTO<List<PostResponseDTO>> getPublicFeed();
}