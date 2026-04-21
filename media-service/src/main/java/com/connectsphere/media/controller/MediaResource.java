package com.connectsphere.media.controller;

import com.connectsphere.media.dto.*;
import com.connectsphere.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * MediaResource - REST Controller for ConnectSphere Media/Story-Service
 *
 * Base path: /api/v1  (via context-path in application.yml)
 *
 * MEDIA ENDPOINTS (all require authentication):
 *   POST   /media/upload                          - Upload image or video file
 *   GET    /media/{mediaId}                       - Get media item by ID
 *   GET    /media/post/{postId}                   - Get all media linked to a post
 *   GET    /media/uploader/{uploaderId}            - Get all media by a user
 *   DELETE /media/{mediaId}                       - Soft-delete media (own or admin)
 *   PATCH  /media/{mediaId}/link/{postId}         - Link media to a post
 *
 * INTERNAL MEDIA ENDPOINTS (inter-service, JWT required):
 *   DELETE /media/post/{postId}/soft-delete       - Soft-delete all media for a deleted post
 *
 * STORY ENDPOINTS (all require authentication):
 *   POST   /stories                               - Create a 24-hour story
 *   GET    /stories/feed?authorIds=1,2,3          - Get active stories from followed users
 *   GET    /stories/{storyId}/view                - View story (increments count)
 *   GET    /stories/user/{authorId}               - Get all active stories by a user
 *   DELETE /stories/{storyId}                     - Delete story (own or admin)
 *
 * userId and role are read from request attributes set by JwtAuthenticationFilter.
 * They are NEVER read from the request body to prevent spoofing.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Media & Story Service", description = "Media upload, CDN linking, story lifecycle, view tracking")
public class MediaResource {

    private final MediaService mediaService;

    // ─── MEDIA ENDPOINTS ─────────────────────────────────────────────────────

    /**
     * Upload an image or video file.
     * Validates MIME type (JPEG, PNG, WebP, MP4) and size limits.
     * Returns CDN URL — use this URL in CreatePostRequest.mediaUrls.
     */
    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload media file",
        description = "Upload an image (JPEG/PNG/WebP) or video (MP4). Returns CDN URL to include in post creation."
    )
    public ResponseEntity<ApiResponseDTO<MediaResponseDTO>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {

        Integer uploaderId = getRequestingUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.uploadMedia(file, uploaderId));
    }

    /**
     * Get a single media item by ID.
     */
    @GetMapping("/media/{mediaId}")
    @Operation(summary = "Get media by ID", description = "Returns media metadata and CDN URL for a specific media item")
    public ResponseEntity<ApiResponseDTO<MediaResponseDTO>> getMediaById(
            @PathVariable Integer mediaId) {

        return ResponseEntity.ok(mediaService.getMediaById(mediaId));
    }

    /**
     * Get all media items linked to a specific post.
     */
    @GetMapping("/media/post/{postId}")
    @Operation(summary = "Get media by post", description = "Returns all active media attached to a specific post")
    public ResponseEntity<ApiResponseDTO<List<MediaResponseDTO>>> getMediaByPost(
            @PathVariable Integer postId) {

        return ResponseEntity.ok(mediaService.getMediaByPost(postId));
    }

    /**
     * Get all media uploaded by a specific user.
     */
    @GetMapping("/media/uploader/{uploaderId}")
    @Operation(summary = "Get media by uploader", description = "Returns all active media uploaded by a specific user")
    public ResponseEntity<ApiResponseDTO<List<MediaResponseDTO>>> getMediaByUploader(
            @PathVariable Integer uploaderId) {

        return ResponseEntity.ok(mediaService.getMediaByUploader(uploaderId));
    }

    /**
     * Soft-delete a media item.
     * Only the uploader or ADMIN/MODERATOR can delete.
     */
    @DeleteMapping("/media/{mediaId}")
    @Operation(
        summary = "Delete media (soft delete)",
        description = "Sets isDeleted=true. Only uploader or Admin/Moderator can delete. Record retained for 30-day audit."
    )
    public ResponseEntity<ApiResponseDTO<String>> deleteMedia(
            @PathVariable Integer mediaId,
            HttpServletRequest httpRequest) {

        Integer requestingUserId = getRequestingUserId(httpRequest);
        String requestingUserRole = getRequestingUserRole(httpRequest);
        return ResponseEntity.ok(
                mediaService.deleteMedia(mediaId, requestingUserId, requestingUserRole));
    }

    /**
     * Link an already-uploaded media item to a post.
     * Called by the frontend after a post is created using pre-uploaded CDN URLs.
     */
    @PatchMapping("/media/{mediaId}/link/{postId}")
    @Operation(
        summary = "Link media to post",
        description = "Associate a pre-uploaded media item with a post. Only the uploader can perform this."
    )
    public ResponseEntity<ApiResponseDTO<MediaResponseDTO>> linkMediaToPost(
            @PathVariable Integer mediaId,
            @PathVariable Integer postId,
            HttpServletRequest httpRequest) {

        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(mediaService.linkMediaToPost(mediaId, postId, userId));
    }

    /**
     * INTERNAL: Soft-delete all media linked to a deleted post.
     * Called by post-service after a post soft-delete operation.
     */
    @DeleteMapping("/media/post/{postId}/soft-delete")
    @Operation(
        summary = "Soft-delete all media for a post [INTERNAL]",
        description = "Called by post-service. Soft-deletes all media linked to a deleted post."
    )
    public ResponseEntity<ApiResponseDTO<String>> softDeleteByPost(
            @PathVariable Integer postId) {

        return ResponseEntity.ok(mediaService.softDeleteByPost(postId));
    }

    // ─── STORY ENDPOINTS ─────────────────────────────────────────────────────

    /**
     * Create a new 24-hour story.
     * Requires a CDN media URL (use /media/upload first).
     */
    @PostMapping("/stories")
    @Operation(
        summary = "Create a story",
        description = "Publish a 24-hour ephemeral story. The mediaUrl must be a CDN URL from /media/upload."
    )
    public ResponseEntity<ApiResponseDTO<StoryResponseDTO>> createStory(
            @Valid @RequestBody CreateStoryRequestDTO request,
            HttpServletRequest httpRequest) {

        Integer authorId = getRequestingUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.createStory(request, authorId));
    }

    /**
     * Get active stories from a list of followed users (news feed stories panel).
     * authorIds = from follow-service (followee IDs of the current user).
     */
    @GetMapping("/stories/feed")
    @Operation(
        summary = "Get stories feed",
        description = "Returns active stories from provided authorIds (followed users). authorIds from follow-service."
    )
    public ResponseEntity<ApiResponseDTO<List<StoryResponseDTO>>> getActiveStories(
            @RequestParam List<Integer> authorIds) {

        return ResponseEntity.ok(mediaService.getActiveStories(authorIds));
    }

    /**
     * View a story — increments view count if viewer is not the author.
     */
    @GetMapping("/stories/{storyId}/view")
    @Operation(
        summary = "View a story",
        description = "Returns story details and increments view count (only if viewer != author)."
    )
    public ResponseEntity<ApiResponseDTO<String>> viewStory(
            @PathVariable Integer storyId,
            HttpServletRequest httpRequest) {

        Integer viewerUserId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(mediaService.viewStory(storyId, viewerUserId));
    }

    /**
     * Get all active stories by a specific user (profile story ring display).
     */
    @GetMapping("/stories/user/{authorId}")
    @Operation(
        summary = "Get stories by user",
        description = "Returns all currently active stories published by a specific user."
    )
    public ResponseEntity<ApiResponseDTO<List<StoryResponseDTO>>> getStoriesByUser(
            @PathVariable Integer authorId) {

        return ResponseEntity.ok(mediaService.getStoriesByUser(authorId));
    }

    /**
     * Delete a story (author's own before expiry, or admin moderation).
     */
    @DeleteMapping("/stories/{storyId}")
    @Operation(
        summary = "Delete a story",
        description = "Sets isActive=false. Only the story author or Admin/Moderator can delete."
    )
    public ResponseEntity<ApiResponseDTO<String>> deleteStory(
            @PathVariable Integer storyId,
            HttpServletRequest httpRequest) {

        Integer requestingUserId = getRequestingUserId(httpRequest);
        String requestingUserRole = getRequestingUserRole(httpRequest);
        return ResponseEntity.ok(
                mediaService.deleteStory(storyId, requestingUserId, requestingUserRole));
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    /**
     * Extract userId set by JwtAuthenticationFilter from request attribute.
     * Throws RuntimeException if not present (should not happen if SecurityConfig is correct).
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
