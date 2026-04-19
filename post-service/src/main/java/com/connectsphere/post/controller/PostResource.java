package com.connectsphere.post.controller;

import com.connectsphere.post.dto.*;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PostResource - REST Controller for ConnectSphere Post Service
 *
 * Base path: /api/v1/posts (via context-path in application.yml)
 *
 * PUBLIC (no JWT required):
 *   GET  /posts/public              - Browse all public posts (guest feed)
 *   GET  /posts/{postId}            - View single post detail
 *   GET  /posts/user/{authorId}     - View user's public timeline
 *   GET  /posts/search?keyword=     - Search posts by keyword
 *   GET  /posts/count/{authorId}    - Get post count for profile badge
 *
 * PROTECTED (valid JWT required):
 *   POST   /posts                   - Create a new post
 *   PUT    /posts/{postId}          - Update post content
 *   DELETE /posts/{postId}          - Soft-delete post (own or Admin)
 *   PATCH  /posts/{postId}/visibility - Change post visibility
 *   GET    /posts/feed              - Personalised news feed
 *
 * INTERNAL (inter-service calls — JWT required):
 *   POST /posts/{postId}/likes/increment      - Called by like-service
 *   POST /posts/{postId}/likes/decrement      - Called by like-service
 *   POST /posts/{postId}/comments/increment   - Called by comment-service
 *   POST /posts/{postId}/comments/decrement   - Called by comment-service
 *   POST /posts/{postId}/shares/increment     - Called on repost
 *
 * userId is read from request attributes set by JwtAuthenticationFilter.
 * It is NEVER read from the request body to prevent spoofing.
 */
@RestController
@RequestMapping("/posts")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Post Service", description = "Post CRUD, news feed, visibility, search, counters")
public class PostResource {

    private final PostService postService;

    // PUBLIC ENDPOINTS

    @GetMapping("/public")
    @Operation(summary = "Browse all public posts",
            description = "Returns all PUBLIC posts ordered by newest first — accessible without authentication")
    public ResponseEntity<ApiResponseDTO<List<PostResponseDTO>>> getPublicPosts() {
        return ResponseEntity.ok(postService.getPublicFeed());
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get single post by ID",
               description = "Returns post detail. PRIVATE posts are hidden from non-authors.")
    public ResponseEntity<ApiResponseDTO<PostResponseDTO>> getPostById(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(postService.getPostById(postId));
    }

    @GetMapping("/user/{authorId}")
    @Operation(summary = "Get all posts by a user",
               description = "Returns public timeline of a user — PUBLIC posts only for non-authors")
    public ResponseEntity<ApiResponseDTO<List<PostResponseDTO>>> getPostsByUser(
            @PathVariable Integer authorId) {
        return ResponseEntity.ok(postService.getPostsByUser(authorId));
    }

    @GetMapping("/search")
    @Operation(summary = "Search posts by keyword",
               description = "Full-text search on PUBLIC post content (case-insensitive)")
    public ResponseEntity<ApiResponseDTO<List<PostResponseDTO>>> searchPosts(
            @RequestParam String keyword) {
        return ResponseEntity.ok(postService.searchPosts(keyword));
    }

    @GetMapping("/count/{authorId}")
    @Operation(summary = "Get post count for a user",
               description = "Used for post count badge on user profile page")
    public ResponseEntity<ApiResponseDTO<Integer>> getPostCount(
            @PathVariable Integer authorId) {
        return ResponseEntity.ok(postService.getPostCount(authorId));
    }

    // PROTECTED ENDPOINTS

    @PostMapping
    @Operation(summary = "Create a new post",
               description = "Creates post for the authenticated user. authorId is read from JWT, not body.")
    public ResponseEntity<ApiResponseDTO<PostResponseDTO>> createPost(
            @Valid @RequestBody CreatePostRequestDTO request,
            HttpServletRequest httpRequest) {

        Integer authorId = getRequestingUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createPost(authorId, request));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "Update post content",
               description = "Only the post author can update their own post")
    public ResponseEntity<ApiResponseDTO<PostResponseDTO>> updatePost(
            @PathVariable Integer postId,
            @Valid @RequestBody UpdatePostRequestDTO request,
            HttpServletRequest httpRequest) {

        Integer requestingUserId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(postService.updatePost(postId, requestingUserId, request));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete a post (soft delete)",
               description = "Post author or Admin/Moderator can delete. Sets isDeleted=true.")
    public ResponseEntity<ApiResponseDTO<String>> deletePost(
            @PathVariable Integer postId,
            HttpServletRequest httpRequest) {

        Integer requestingUserId = getRequestingUserId(httpRequest);
        String requestingUserRole = getRequestingUserRole(httpRequest);
        return ResponseEntity.ok(
                postService.deletePost(postId, requestingUserId, requestingUserRole));
    }

    @PatchMapping("/{postId}/visibility")
    @Operation(summary = "Change post visibility",
               description = "Switch between PUBLIC, FOLLOWERS_ONLY, PRIVATE. Author only.")
    public ResponseEntity<ApiResponseDTO<PostResponseDTO>> changeVisibility(
            @PathVariable Integer postId,
            @RequestParam String visibility,
            HttpServletRequest httpRequest) {

        Integer requestingUserId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(
                postService.changeVisibility(postId, requestingUserId, visibility));
    }

    @GetMapping("/feed")
    @Operation(summary = "Get personalised news feed",
               description = "Returns posts from followees. Pass followeeIds as query params " +
                             "(obtained from follow-service by the frontend or API gateway).")
    public ResponseEntity<ApiResponseDTO<List<PostResponseDTO>>> getFeed(
            @RequestParam List<Integer> followeeIds) {
        return ResponseEntity.ok(postService.getFeedForUser(followeeIds));
    }

    // INTERNAL COUNTER ENDPOINTS (called by other microservices)

    @PostMapping("/{postId}/likes/increment")
    @Operation(summary = "Increment likes count — called by like-service")
    public ResponseEntity<ApiResponseDTO<String>> incrementLikes(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(postService.incrementLikes(postId));
    }

    @PostMapping("/{postId}/likes/decrement")
    @Operation(summary = "Decrement likes count — called by like-service on unlike")
    public ResponseEntity<ApiResponseDTO<String>> decrementLikes(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(postService.decrementLikes(postId));
    }

    @PostMapping("/{postId}/comments/increment")
    @Operation(summary = "Increment comments count — called by comment-service")
    public ResponseEntity<ApiResponseDTO<String>> incrementComments(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(postService.incrementComments(postId));
    }

    @PostMapping("/{postId}/comments/decrement")
    @Operation(summary = "Decrement comments count — called by comment-service on delete")
    public ResponseEntity<ApiResponseDTO<String>> decrementComments(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(postService.decrementComments(postId));
    }

    @PostMapping("/{postId}/shares/increment")
    @Operation(summary = "Increment shares count — called on repost")
    public ResponseEntity<ApiResponseDTO<String>> incrementShares(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(postService.incrementShares(postId));
    }

    // PRIVATE HELPERS

    /**
     * Extract userId set by JwtAuthenticationFilter from request attribute.
     * Throws 401 if not present (should not happen if SecurityConfig is correct).
     */
    private Integer getRequestingUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("requestingUserId");
        if (userId == null) {
            throw new RuntimeException("Unauthorized: userId not found in request");
        }
        return (Integer) userId;
    }

    /**
     * Extract role set by JwtAuthenticationFilter from request attribute.
     */
    private String getRequestingUserRole(HttpServletRequest request) {
        Object role = request.getAttribute("requestingUserRole");
        return role != null ? (String) role : "USER";
    }
}