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

@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Media & Story Service", description = "Media upload, CDN linking, story lifecycle, view tracking")
public class MediaResource {

    private final MediaService mediaService;

    @org.springframework.beans.factory.annotation.Value("${media.storage-base-path}")
    private String storageBasePath;

    // ─── MEDIA ENDPOINTS ───

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

    // ─── STORY ENDPOINTS ──

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

    @GetMapping("/stories/feed")
    @Operation(
        summary = "Get stories feed",
        description = "Returns active stories. If authorIds are not provided, it automatically fetches stories for the authenticated user and their followees."
    )
    public ResponseEntity<ApiResponseDTO<List<StoryResponseDTO>>> getActiveStories(
            @RequestParam(required = false) List<Integer> authorIds,
            HttpServletRequest httpRequest) {

        if (authorIds == null || authorIds.isEmpty()) {
            Integer userId = getRequestingUserId(httpRequest);
            String authHeader = httpRequest.getHeader("Authorization");
            return ResponseEntity.ok(mediaService.getActiveStoriesForUser(userId, authHeader));
        }

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
        String authHeader = httpRequest.getHeader("Authorization");
        return ResponseEntity.ok(mediaService.viewStory(storyId, viewerUserId, authHeader));
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

    @GetMapping("/stories/{storyId}/viewers")
    @Operation(summary = "Get story viewer list",
               description = "Returns list of user profiles who viewed the story. Only accessible by story author.")
    public ResponseEntity<ApiResponseDTO<List<java.util.Map<String, Object>>>> getStoryViewers(
            @PathVariable Integer storyId,
            HttpServletRequest httpRequest) {
        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.ok(mediaService.getStoryViewers(storyId, userId));
    }
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

    /**
     * Serve uploaded files from the local uploads directory.
     * This simulates a CDN serving files.
     */
    @GetMapping("/media/cdn/{fileName}")
    @Operation(summary = "Serve media file", description = "Serves the actual file bytes for a given filename. Simulates CDN behavior.")
    public ResponseEntity<org.springframework.core.io.Resource> serveFile(@PathVariable String fileName) {
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(storageBasePath).resolve(fileName).normalize();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                String contentType = "application/octet-stream";
                try {
                    contentType = java.nio.file.Files.probeContentType(filePath);
                } catch (java.io.IOException e) {
                    log.error("Could not determine file type.");
                }

                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (java.net.MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ─── PRIVATE HELPERS ─────

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
