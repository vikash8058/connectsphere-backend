package com.connectsphere.comment.controller;

import com.connectsphere.comment.dto.*;
import com.connectsphere.comment.service.CommentService;
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
 * CommentResource - REST Controller for ConnectSphere Comment Service
 *
 * Base path: /api/v1/comments (via context-path in application.yml)
 *
 * PUBLIC (no JWT required):
 *   GET /comments/post/{postId}               - All comments for a post
 *   GET /comments/post/{postId}/top-level     - Top-level comments only
 *   GET /comments/{commentId}                 - Single comment by ID
 *   GET /comments/{commentId}/replies         - Replies to a comment
 *   GET /comments/count/{postId}              - Comment count for post badge
 *
 * PROTECTED (valid JWT required):
 *   POST   /comments                          - Add a comment or reply
 *   PUT    /comments/{commentId}              - Edit comment content (author only)
 *   DELETE /comments/{commentId}              - Soft-delete (author/admin/mod)
 *   POST   /comments/{commentId}/like         - Like a comment
 *   POST   /comments/{commentId}/unlike       - Unlike a comment
 *   GET    /comments/user/{authorId}          - All comments by a user
 *
 * userId is read from request attributes set by JwtAuthenticationFilter.
 */
@RestController
@RequestMapping("/comments")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Comment Service",
     description = "Threaded comments and replies on posts, likes on comments")
public class CommentResource {

    private final CommentService commentService;

    // PUBLIC ENDPOINTS

    @GetMapping("/post/{postId}")
    @Operation(summary = "Get all comments for a post",
               description = "Returns all comments including soft-deleted (with placeholder text)")
    public ResponseEntity<ApiResponseDTO<List<CommentResponseDTO>>> getCommentsByPost(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(commentService.getCommentsByPost(postId));
    }

    @GetMapping("/post/{postId}/top-level")
    @Operation(summary = "Get top-level comments for a post",
               description = "Returns only top-level comments (parentCommentId = null). " +
                             "Load replies separately via /{commentId}/replies.")
    public ResponseEntity<ApiResponseDTO<List<CommentResponseDTO>>> getTopLevelComments(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(commentService.getTopLevelComments(postId));
    }

    @GetMapping("/{commentId}")
    @Operation(summary = "Get a single comment by ID",
               description = "Returns even soft-deleted comments (with placeholder) to preserve thread")
    public ResponseEntity<ApiResponseDTO<CommentResponseDTO>> getCommentById(
            @PathVariable Integer commentId) {
        return ResponseEntity.ok(commentService.getCommentById(commentId));
    }

    @GetMapping("/{commentId}/replies")
    @Operation(summary = "Get all replies to a comment",
               description = "Returns second-level thread replies for a parent comment")
    public ResponseEntity<ApiResponseDTO<List<CommentResponseDTO>>> getReplies(
            @PathVariable Integer commentId) {
        return ResponseEntity.ok(commentService.getReplies(commentId));
    }

    @GetMapping("/count/{postId}")
    @Operation(summary = "Get comment count for a post",
               description = "Used for comment count badge on post cards")
    public ResponseEntity<ApiResponseDTO<Integer>> getCommentCount(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(commentService.getCommentCount(postId));
    }

    // PROTECTED ENDPOINTS

    @PostMapping
    @Operation(summary = "Add a comment or reply",
               description = "Set parentCommentId to null for top-level, " +
                             "or to a commentId for replies. authorId comes from JWT.")
    public ResponseEntity<ApiResponseDTO<CommentResponseDTO>> addComment(
            @Valid @RequestBody AddCommentRequestDTO request,
            HttpServletRequest httpRequest) {

        Integer authorId = getRequestingUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(authorId, request));
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Update comment content",
               description = "Only the comment author can update their own comment")
    public ResponseEntity<ApiResponseDTO<CommentResponseDTO>> updateComment(
            @PathVariable Integer commentId,
            @Valid @RequestBody UpdateCommentRequestDTO request,
            HttpServletRequest httpRequest) {

        Integer requestingUserId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(commentService.updateComment(commentId, requestingUserId, request));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment (soft delete)",
               description = "Author or Admin/Moderator can delete. " +
                             "Sets isDeleted=true, preserves thread structure.")
    public ResponseEntity<ApiResponseDTO<String>> deleteComment(
            @PathVariable Integer commentId,
            HttpServletRequest httpRequest) {

        Integer requestingUserId = getRequestingUserId(httpRequest);
        String requestingUserRole = getRequestingUserRole(httpRequest);
        return ResponseEntity.ok(
                commentService.deleteComment(commentId, requestingUserId, requestingUserRole));
    }

    @PostMapping("/{commentId}/like")
    @Operation(summary = "Like a comment",
               description = "Increments the likesCount on the comment atomically")
    public ResponseEntity<ApiResponseDTO<String>> likeComment(
            @PathVariable Integer commentId) {
        return ResponseEntity.ok(commentService.likeComment(commentId));
    }

    @PostMapping("/{commentId}/unlike")
    @Operation(summary = "Unlike a comment",
               description = "Decrements the likesCount on the comment atomically (min 0)")
    public ResponseEntity<ApiResponseDTO<String>> unlikeComment(
            @PathVariable Integer commentId) {
        return ResponseEntity.ok(commentService.unlikeComment(commentId));
    }

    @GetMapping("/user/{authorId}")
    @Operation(summary = "Get all comments by a user",
               description = "Returns comment history of a user (non-deleted only)")
    public ResponseEntity<ApiResponseDTO<List<CommentResponseDTO>>> getCommentsByUser(
            @PathVariable Integer authorId) {
        return ResponseEntity.ok(commentService.getCommentsByUser(authorId));
    }

    // PRIVATE HELPERS

    private Integer getRequestingUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("requestingUserId");
        if (userId == null) {
            throw new RuntimeException("Unauthorized: userId not found in request");
        }
        return (Integer) userId;
    }

    private String getRequestingUserRole(HttpServletRequest request) {
        Object role = request.getAttribute("requestingUserRole");
        return role != null ? (String) role : "USER";
    }
}