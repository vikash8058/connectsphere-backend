package com.connectsphere.media.service;

import com.connectsphere.media.client.PostServiceClient;
import com.connectsphere.media.dto.*;
import com.connectsphere.media.entity.*;
import com.connectsphere.media.exception.*;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final StoryRepository storyRepository;
    private final PostServiceClient postServiceClient;
    private final com.connectsphere.media.client.FollowServiceClient followServiceClient;
    private final com.connectsphere.media.repository.StoryViewRepository storyViewRepository;
    private final com.connectsphere.media.client.AuthServiceClient authServiceClient;
    private final StorageService storageService;

    @Value("${media.cdn-base-url}")
    private String cdnBaseUrl;

    @Value("${media.max-image-size-kb}")
    private long maxImageSizeKb;

    @Value("${media.max-video-size-kb}")
    private long maxVideoSizeKb;

    @Value("${media.allowed-image-types}")
    private String allowedImageTypes;

    @Value("${media.allowed-video-types}")
    private String allowedVideoTypes;

    // ─── MEDIA OPERATIONS ────

    /**
     * Upload a media file, validate MIME type & size, simulate CDN storage,
     * persist, and return CDN URL.
     */
    @Override
    @Transactional
    public ApiResponseDTO<MediaResponseDTO> uploadMedia(MultipartFile file, Integer uploaderId) {
        log.info("MediaServiceImpl.uploadMedia() - uploaderId={}, filename={}, size={}KB",
                uploaderId, file.getOriginalFilename(), file.getSize() / 1024);

        // Validate file is not empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty. Please select a valid file.");
        }

        String mimeType = file.getContentType();
        if (mimeType == null) {
            throw new UnsupportedMediaTypeException("Cannot determine file type. Upload a valid image or video.");
        }

        // Determine media type and validate MIME
        MediaType mediaType = resolveMediaType(mimeType);

        // Validate file size
        long sizeKb = file.getSize() / 1024;
        validateFileSize(mediaType, sizeKb, mimeType);

        // Simulate CDN storage — generate unique URL
        // In production: upload bytes to AWS S3, get back a CloudFront CDN URL
        // 1. Generate unique filename
        String fileName = generateUniqueFileName(file.getOriginalFilename(), mimeType);
        
        // 2. Save file using storage service
        String cdnUrl;
        try {
            cdnUrl = storageService.store(file.getInputStream(), fileName, mimeType);
        } catch (java.io.IOException e) {
            log.error("Failed to read upload stream: {}", e.getMessage());
            throw new RuntimeException("Could not read uploaded file.");
        }

        // Persist Media entity
        Media media = Media.builder()
                .uploaderId(uploaderId)
                .url(cdnUrl)
                .mediaType(mediaType)
                .sizeKb(sizeKb)
                .mimeType(mimeType)
                .linkedPostId(null)  // Will be linked to a post later
                .isDeleted(false)
                .build();

        Media saved = mediaRepository.save(media);
        log.info("Media saved successfully: mediaId={}, url={}", saved.getMediaId(), saved.getUrl());

        return ApiResponseDTO.success("Media uploaded successfully", toMediaResponseDTO(saved));
    }

    /**
     * Get all active media linked to a specific post.
     */
    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO<List<MediaResponseDTO>> getMediaByPost(Integer postId) {
        log.debug("MediaServiceImpl.getMediaByPost() - postId={}", postId);

        List<MediaResponseDTO> mediaList = mediaRepository
                .findByLinkedPostIdAndIsDeletedFalse(postId)
                .stream()
                .map(this::toMediaResponseDTO)
                .collect(Collectors.toList());

        return ApiResponseDTO.success("Media for post retrieved", mediaList);
    }

    /**
     * Get a single media item by ID.
     */
    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO<MediaResponseDTO> getMediaById(Integer mediaId) {
        log.debug("MediaServiceImpl.getMediaById() - mediaId={}", mediaId);

        Media media = mediaRepository.findByMediaIdAndIsDeletedFalse(mediaId)
                .orElseThrow(() -> new MediaNotFoundException(
                        "Media not found with ID: " + mediaId));

        return ApiResponseDTO.success("Media retrieved", toMediaResponseDTO(media));
    }

    /**
     * Soft-delete a media item (own or admin).
     */
    @Override
    @Transactional
    public ApiResponseDTO<String> deleteMedia(Integer mediaId, Integer requestingUserId, String requestingUserRole) {
        log.info("MediaServiceImpl.deleteMedia() - mediaId={}, requestingUserId={}, role={}",
                mediaId, requestingUserId, requestingUserRole);

        Media media = mediaRepository.findByMediaIdAndIsDeletedFalse(mediaId)
                .orElseThrow(() -> new MediaNotFoundException(
                        "Media not found with ID: " + mediaId));

        // Only uploader or ADMIN/MODERATOR can delete
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requestingUserRole)
                || "MODERATOR".equalsIgnoreCase(requestingUserRole);
        if (!media.getUploaderId().equals(requestingUserId) && !isAdmin) {
            throw new UnauthorizedActionException(
                    "You are not authorized to delete media ID: " + mediaId);
        }

        mediaRepository.softDeleteByMediaId(mediaId);
        log.info("Media soft-deleted: mediaId={}", mediaId);

        return ApiResponseDTO.success("Media deleted successfully");
    }

    /**
     * Soft-delete all media linked to a deleted post (inter-service call from post-service).
     * IMPORTANT: Only soft-deletes if media exists for the given post.
     * If no media is linked to the post, still returns success (idempotent operation).
     */
    @Override
    @Transactional
    public ApiResponseDTO<String> softDeleteByPost(Integer postId) {
        log.info("MediaServiceImpl.softDeleteByPost() - postId={}", postId);
        
        // Verify post exists by calling post-service
        try {
            ApiResponseDTO<PostSummaryDTO> postResponse = postServiceClient.getPostById(postId);
            if (postResponse == null || !postResponse.isSuccess() || postResponse.getData() == null) {
                throw new PostNotFoundException("Post not found with id: " + postId);
            }
            log.debug("Post {} exists and is valid. Proceeding to soft-delete linked media.", postId);
        } catch (PostNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Post-service unavailable or post {} not found. Attempting soft-delete anyway.", postId);
            throw new PostNotFoundException("Post not found or post-service unavailable for id: " + postId);
        }
        
        // Get media count before deletion (for logging)
        List<Media> mediaToDelete = mediaRepository.findByLinkedPostIdAndIsDeletedFalse(postId);
        int mediaCount = mediaToDelete.size();
        
        // Soft-delete all media linked to this post
        mediaRepository.softDeleteByLinkedPostId(postId);
        
        log.info("Media soft-deleted for post {}: {} item(s) deleted", postId, mediaCount);
        
        if (mediaCount == 0) {
            return ApiResponseDTO.success("Post " + postId + " has no media to delete (or already deleted)");
        }
        
        return ApiResponseDTO.success(mediaCount + " media item(s) for post " + postId + " soft-deleted");
    }

     /**
      * Link an already-uploaded media item to a post.
      * IMPORTANT: Both the media and the post must belong to the same user.
      * Media can only be linked to posts created by the same author.
      */
     @Override
     @Transactional
     public ApiResponseDTO<MediaResponseDTO> linkMediaToPost(Integer mediaId, Integer postId, Integer userId) {
         log.info("MediaServiceImpl.linkMediaToPost() - mediaId={}, postId={}, userId={}", mediaId, postId, userId);

         // Step 1: Verify media exists, is not deleted, and belongs to the requesting user
         Media media = mediaRepository.findByMediaIdAndIsDeletedFalse(mediaId)
                 .orElseThrow(() -> new MediaNotFoundException("Media not found with ID: " + mediaId));

         if (!media.getUploaderId().equals(userId)) {
             throw new UnauthorizedActionException(
                     "You can only link your own media to a post.");
         }

         // Step 2: Verify post exists and belongs to the requesting user
         try {
             ApiResponseDTO<PostSummaryDTO> postResponse = postServiceClient.getPostById(postId);
             if (postResponse == null || !postResponse.isSuccess() || postResponse.getData() == null) {
                 throw new PostNotFoundException("Post not found with id: " + postId);
             }

             Integer postAuthorId = postResponse.getData().getAuthorId();
             if (postAuthorId == null ) {
                 throw new PostNotFoundException("Post not found with id: " + postId);
             }

             // CRITICAL: Media uploader and post author MUST be the same person
             if (!postAuthorId.equals(userId)) {
                 throw new UnauthorizedActionException(
                         "You can only link media to your own posts. Post " + postId + " does not belong to you.");
             }

         } catch (PostNotFoundException | UnauthorizedActionException ex) {
             throw ex;
         } catch (Exception ex) {
             throw new PostNotFoundException("Post not found or post-service unavailable for id: " + postId);
         }

         // Step 3: Link the media to the post
         mediaRepository.linkMediaToPost(mediaId, postId);

         // Refresh entity for response
         Media updated = mediaRepository.findByMediaIdAndIsDeletedFalse(mediaId)
                 .orElseThrow(() -> new MediaNotFoundException("Media not found after linking"));

         return ApiResponseDTO.success("Media linked to post successfully", toMediaResponseDTO(updated));
     }

    /**
     * Get all active media uploaded by a specific user.
     */
    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO<List<MediaResponseDTO>> getMediaByUploader(Integer uploaderId) {
        log.debug("MediaServiceImpl.getMediaByUploader() - uploaderId={}", uploaderId);

        List<MediaResponseDTO> mediaList = mediaRepository
                .findByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(uploaderId)
                .stream()
                .map(this::toMediaResponseDTO)
                .collect(Collectors.toList());

        return ApiResponseDTO.success("Media by uploader retrieved", mediaList);
    }

    // ─── STORY OPERATIONS ───
    /**
     * Create a new 24-hour ephemeral story.
     * expiresAt = now() + 24 hours.
     */
    @Override
    @Transactional
    public ApiResponseDTO<StoryResponseDTO> createStory(CreateStoryRequestDTO request, Integer authorId) {
        log.info("MediaServiceImpl.createStory() - authorId={}", authorId);

        LocalDateTime now = LocalDateTime.now();

        Story story = Story.builder()
                .authorId(authorId)
                .mediaUrl(request.getMediaUrl())
                .caption(request.getCaption())
                .mediaType(request.getMediaType())
                .viewsCount(0)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .visibility(request.getVisibility() != null ? request.getVisibility() : Visibility.PUBLIC)
                .isActive(true)
                .build();

        Story saved = storyRepository.save(story);
        log.info("Story created: storyId={}, expiresAt={}", saved.getStoryId(), saved.getExpiresAt());

        return ApiResponseDTO.success("Story created successfully", toStoryResponseDTO(saved));
    }

    /**
     * Get all active stories from a list of author IDs (followed users).
     */
    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO<List<StoryResponseDTO>> getActiveStories(List<Integer> authorIds) {
        log.debug("MediaServiceImpl.getActiveStories() - authorIds={}", authorIds);

        if (authorIds == null || authorIds.isEmpty()) {
            return ApiResponseDTO.success("No followees provided", List.of());
        }

        // Since this is a direct ID-based fetch (likely for profile rings),
        // we use -1 as the userId to indicate 'General Viewer' (no private access)
        List<StoryResponseDTO> stories = storyRepository
                .findActiveStoriesForFeed(-1, authorIds)
                .stream()
                .map(this::toStoryResponseDTO)
                .collect(Collectors.toList());

        return ApiResponseDTO.success("Active stories retrieved", stories);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO<List<StoryResponseDTO>> getActiveStoriesForUser(Integer requestingUserId, String authHeader) {
        log.info("Building story feed for user: {}", requestingUserId);

        java.util.Set<Integer> authorIds = new java.util.HashSet<>();
        // 1. Always include self
        authorIds.add(requestingUserId);

        // 2. Fetch followees - wrapped in try/catch to be resilient
        try {
            if (followServiceClient != null) {
                ApiResponseDTO<List<Integer>> response = followServiceClient.getFolloweeIds(requestingUserId);
                if (response != null && response.isSuccess() && response.getData() != null) {
                    authorIds.addAll(response.getData());
                }
            }
        } catch (Exception e) {
            log.error("Resilience: Follow-service call failed for stories. Proceeding with user's own stories only. Error: {}", e.getMessage());
        }

        // 3. Prepare followee list (excluding self)
        List<Integer> followeeIds = new java.util.ArrayList<>(authorIds);
        followeeIds.remove(requestingUserId);

        // JPA Safeguard: Some environments throw 500 on empty IN clauses.
        // We use a dummy ID (-1) if the user follows no one.
        if (followeeIds.isEmpty()) {
            followeeIds.add(-1);
        }

        try {
            List<StoryResponseDTO> stories = storyRepository
                    .findActiveStoriesForFeed(requestingUserId, followeeIds)
                    .stream()
                    .map(this::toStoryResponseDTO)
                    .collect(Collectors.toList());

            return ApiResponseDTO.success("Story feed retrieved", stories);
        } catch (Exception e) {
            log.error("Critical: Failed to fetch stories from repository: {}", e.getMessage());
            // Last resort: return empty list instead of 500
            return ApiResponseDTO.success("System fallback: Story feed temporarily limited", List.of());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO<List<StoryResponseDTO>> getAllActiveStories() {
        log.info("Fetching all active stories for admin stats");
        List<StoryResponseDTO> stories = storyRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(this::toStoryResponseDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("All active stories retrieved", stories);
    }

    /**
     * Increment view count when another user opens a story.
     * Does NOT increment if viewer == author (self-view).
     * Enforces visibility rules: PUBLIC, FOLLOWERS_ONLY, or PRIVATE.
     */
    @Override
    @Transactional
    public ApiResponseDTO<String> viewStory(Integer storyId, Integer viewerUserId, String authHeader) {
        log.debug("MediaServiceImpl.viewStory() - storyId={}, viewerUserId={}", storyId, viewerUserId);

        Story story = storyRepository.findByStoryIdAndIsActiveTrue(storyId)
                .orElseThrow(() -> new StoryNotFoundException(
                        "Story not found or has expired: " + storyId));

        // 1. Visibility Check
        if (story.getVisibility() == Visibility.PRIVATE) {
            if (!story.getAuthorId().equals(viewerUserId)) {
                throw new UnauthorizedActionException("This story is private");
            }
        } else if (story.getVisibility() == Visibility.FOLLOWERS_ONLY) {
            if (!story.getAuthorId().equals(viewerUserId)) {
                boolean isFollowing = false;
                try {
                    // Check if viewer follows author
                    ApiResponseDTO<Boolean> checkRes = followServiceClient.isFollowing(story.getAuthorId(), authHeader);
                    if (checkRes != null && checkRes.isSuccess()) {
                        isFollowing = Boolean.TRUE.equals(checkRes.getData());
                    }
                } catch (Exception e) {
                    log.error("Failed to verify follow status: {}", e.getMessage());
                }
                if (!isFollowing) {
                    throw new UnauthorizedActionException("This story is for followers only");
                }
            }
        }

        // 2. Case study 2.6: "Story view counts are incremented when another user opens a story"
        // Do NOT increment if the author views their own story
        if (!story.getAuthorId().equals(viewerUserId)) {
            // Unique view tracking
            if (!storyViewRepository.existsByStoryAndViewerUserId(story, viewerUserId)) {
                StoryView sv = StoryView.builder()
                        .story(story)
                        .viewerUserId(viewerUserId)
                        .build();
                storyViewRepository.save(sv);
                
                storyRepository.incrementViewsCount(storyId);
                log.debug("Story view count incremented: storyId={}", storyId);
            }
        }

        return ApiResponseDTO.success("Story viewed");
    }

    /**
     * Author manually deletes their story before expiry (or admin removes it).
     */
    @Override
    @Transactional
    public ApiResponseDTO<String> deleteStory(Integer storyId, Integer requestingUserId, String requestingUserRole) {
        log.info("MediaServiceImpl.deleteStory() - storyId={}, requestingUserId={}, role={}",
                storyId, requestingUserId, requestingUserRole);

        Story story = storyRepository.findByStoryIdAndIsActiveTrue(storyId)
                .orElseThrow(() -> new StoryNotFoundException(
                        "Story not found or already expired: " + storyId));

        // Only author or ADMIN/MODERATOR can delete a story
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requestingUserRole)
                || "MODERATOR".equalsIgnoreCase(requestingUserRole);
        if (!story.getAuthorId().equals(requestingUserId) && !isAdmin) {
            throw new UnauthorizedActionException(
                    "You are not authorized to delete story ID: " + storyId);
        }

        storyRepository.deactivateByStoryId(storyId);
        log.info("Story deleted (deactivated): storyId={}", storyId);

        return ApiResponseDTO.success("Story deleted successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO<List<Map<String, Object>>> getStoryViewers(Integer storyId, Integer requestingUserId) {
        log.info("Fetching viewers for storyId={} by user={}", storyId, requestingUserId);

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException("Story not found: " + storyId));

        // Ownership check - only author can see the viewer list
        if (!story.getAuthorId().equals(requestingUserId)) {
            throw new UnauthorizedActionException("Only the author can see the viewer list");
        }

        List<Integer> viewerIds = storyViewRepository.findViewerUserIdsByStoryId(storyId);
        List<Map<String, Object>> viewers = new ArrayList<>();

        for (Integer vId : viewerIds) {
            try {
                ApiResponseDTO<Map<String, Object>> userRes = authServiceClient.getUserById(vId);
                if (userRes != null && userRes.isSuccess() && userRes.getData() != null) {
                    viewers.add(userRes.getData());
                }
            } catch (Exception e) {
                log.error("Failed to fetch viewer info for userId={}: {}", vId, e.getMessage());
                // Fallback with just ID if auth-service fails
                viewers.add(Map.of("userId", vId, "username", "Unknown User"));
            }
        }

        return ApiResponseDTO.success("Story viewers retrieved", viewers);
    }

    /**
     * Get all active stories by a specific user (for profile story ring display).
     */
    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO<List<StoryResponseDTO>> getStoriesByUser(Integer authorId) {
        log.debug("MediaServiceImpl.getStoriesByUser() - authorId={}", authorId);

        List<StoryResponseDTO> stories = storyRepository
                .findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(authorId)
                .stream()
                .map(this::toStoryResponseDTO)
                .collect(Collectors.toList());

        return ApiResponseDTO.success("Stories by user retrieved", stories);
    }

    /**
     * Scheduler-triggered batch expiry of stories older than 24 hours.
     * "Stories are purged within 5 minutes of their 24-hour expiry via a scheduled cleanup job" (NFR)
     */
    @Override
    @Transactional
    public int expireOldStories() {
        LocalDateTime now = LocalDateTime.now();
        int expiredCount = storyRepository.deactivateExpiredStories(now);
        log.info("StoryExpiryScheduler: {} stories expired at {}", expiredCount, now);
        return expiredCount;
    }

    // ─── PRIVATE HELPERS ───

    /**
     * Resolve MediaType enum from MIME type string.
     */
    private MediaType resolveMediaType(String mimeType) {
        List<String> allowedImages = Arrays.asList(allowedImageTypes.split(","));
        List<String> allowedVideos = Arrays.asList(allowedVideoTypes.split(","));

        if (allowedImages.contains(mimeType.trim())) {
            return MediaType.IMAGE;
        } else if (allowedVideos.contains(mimeType.trim())) {
            return MediaType.VIDEO;
        } else {
            throw new UnsupportedMediaTypeException(
                    "Unsupported file type: " + mimeType +
                    ". Allowed: JPEG, PNG, WebP images and MP4 videos.");
        }
    }

    /**
     * Validate file size against configurable limits.
     * Case study 2.6: "up to configurable size limits"
     */
    private void validateFileSize(MediaType mediaType, long sizeKb, String mimeType) {
        if (mediaType == MediaType.IMAGE && sizeKb > maxImageSizeKb) {
            throw new IllegalArgumentException(
                    String.format("Image file exceeds maximum size of %d KB (%.2f MB). Uploaded: %d KB.",
                            maxImageSizeKb, maxImageSizeKb / 1024.0, sizeKb));
        }
        if (mediaType == MediaType.VIDEO && sizeKb > maxVideoSizeKb) {
            throw new IllegalArgumentException(
                    String.format("Video file exceeds maximum size of %d KB (%.0f MB). Uploaded: %d KB.",
                            maxVideoSizeKb, maxVideoSizeKb / 1024.0, sizeKb));
        }
    }

    /**
     * Generate a unique file name to prevent collisions on CDN storage.
     * Pattern: UUID + original extension.
     */
    private String generateUniqueFileName(String originalFilename, String mimeType) {
        String extension = resolveExtension(mimeType);
        return UUID.randomUUID().toString() + "." + extension;
    }

    /**
     * Resolve file extension from MIME type.
     */
    private String resolveExtension(String mimeType) {
        return switch (mimeType.trim()) {
            case "image/jpeg"      -> "jpg";
            case "image/png"       -> "png";
            case "image/webp"      -> "webp";
            case "video/mp4"       -> "mp4";
            case "video/quicktime" -> "mov";
            case "video/webm"      -> "webm";
            case "video/ogg"       -> "ogg";
            default                -> "bin";
        };
    }

    /**
     * Build the full CDN URL for a file name.
     * In production: return the CloudFront URL after uploading to S3.
     * Format: https://cdn.connectsphere.com/media/{year}/{month}/{uuid}.ext
     */
    private String buildCdnUrl(String fileName) {
        // Points to our local media-service endpoint that serves files
        return String.format("%s/%s", cdnBaseUrl, fileName);
    }

    /**
     * Map Media entity → MediaResponseDTO.
     */
    private MediaResponseDTO toMediaResponseDTO(Media media) {
        return MediaResponseDTO.builder()
                .mediaId(media.getMediaId())
                .uploaderId(media.getUploaderId())
                .url(media.getUrl())
                .mediaType(media.getMediaType())
                .sizeKb(media.getSizeKb())
                .mimeType(media.getMimeType())
                .linkedPostId(media.getLinkedPostId())
                .uploadedAt(media.getUploadedAt())
                .build();
    }

    /**
     * Map Story entity → StoryResponseDTO.
     */
    private StoryResponseDTO toStoryResponseDTO(Story story) {
        StoryResponseDTO dto = StoryResponseDTO.builder()
                .storyId(story.getStoryId())
                .authorId(story.getAuthorId())
                .mediaUrl(story.getMediaUrl())
                .caption(story.getCaption())
                .mediaType(story.getMediaType())
                .viewsCount(story.getViewsCount())
                .expiresAt(story.getExpiresAt())
                .createdAt(story.getCreatedAt())
                .isActive(story.getIsActive())
                .visibility(story.getVisibility())
                .build();

        // Fetch author profile details from auth-service
        try {
            ApiResponseDTO<java.util.Map<String, Object>> userRes = authServiceClient.getUserById(story.getAuthorId());
            if (userRes != null && userRes.isSuccess() && userRes.getData() != null) {
                java.util.Map<String, Object> userData = userRes.getData();
                dto.setAuthorUsername((String) userData.get("username"));
                dto.setAuthorProfilePic((String) userData.get("profilePicUrl"));
            }
        } catch (Exception e) {
            log.error("Failed to fetch author details for story {}: {}", story.getStoryId(), e.getMessage());
            dto.setAuthorUsername("Unknown");
        }

        return dto;
    }
}
