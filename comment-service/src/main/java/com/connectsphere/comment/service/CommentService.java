package com.connectsphere.comment.service;

import com.connectsphere.comment.dto.*;

import java.util.List;

/**
 * CommentService - Business Contract (Interface)
 *
 * Declares all operations as per ConnectSphere case study section 4.3:
 *   addComment(), getCommentsByPost(), getCommentById(), getReplies(),
 *   updateComment(), deleteComment(), getCommentsByUser(),
 *   likeComment(), unlikeComment(), getCommentCount()
 *
 * Follows the same interface/implementation separation pattern
 * as AuthService/AuthServiceImpl and PostService/PostServiceImpl.
 */
public interface CommentService {

    /**
     * Add a top-level comment or a reply to an existing comment.
     * authorId comes from JWT token, not from request body.
     * After success, calls post-service to increment commentsCount.
     */
    ApiResponseDTO<CommentResponseDTO> addComment(Integer authorId,
                                                   AddCommentRequestDTO request);

    /**
     * Get ALL comments for a post (top-level + replies).
     * Soft-deleted comments returned with placeholder content.
     */
    ApiResponseDTO<List<CommentResponseDTO>> getCommentsByPost(Integer postId);

    /**
     * Get only TOP-LEVEL comments for a post (parentCommentId = null).
     * Replies loaded separately on demand via getReplies().
     */
    ApiResponseDTO<List<CommentResponseDTO>> getTopLevelComments(Integer postId);

    /**
     * Get a single comment by ID.
     * Returns even soft-deleted (with placeholder) to support thread structure.
     */
    ApiResponseDTO<CommentResponseDTO> getCommentById(Integer commentId);

    /**
     * Get all replies to a specific comment (second-level thread).
     */
    ApiResponseDTO<List<CommentResponseDTO>> getReplies(Integer parentCommentId);

    /**
     * Update content of a comment.
     * Only the comment author can update their own comment.
     */
    ApiResponseDTO<CommentResponseDTO> updateComment(Integer commentId,
                                                      Integer requestingUserId,
                                                      UpdateCommentRequestDTO request);

    /**
     * Soft-delete a comment (isDeleted = true).
     * Only the comment author or ADMIN/MODERATOR can delete.
     * After success, calls post-service to decrement commentsCount.
     */
    ApiResponseDTO<String> deleteComment(Integer commentId, Integer requestingUserId,
                                          String requestingUserRole);

    /**
     * Get all comments written by a specific user (their comment history).
     */
    ApiResponseDTO<List<CommentResponseDTO>> getCommentsByUser(Integer authorId);

    /**
     * Like a comment — increments likesCount atomically.
     */
    ApiResponseDTO<String> likeComment(Integer commentId);

    /**
     * Unlike a comment — decrements likesCount atomically (min 0).
     */
    ApiResponseDTO<String> unlikeComment(Integer commentId);

    /**
     * Get total comment count for a post (for post badge display).
     */
    ApiResponseDTO<Integer> getCommentCount(Integer postId);
}