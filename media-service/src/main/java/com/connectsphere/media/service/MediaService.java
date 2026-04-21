package com.connectsphere.media.service;

import com.connectsphere.media.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * MediaService - Business Contract for Media/Story-Service
 *
 * Declares all operations as per ConnectSphere case study section 4.7:
 *
 * Media operations:
 *   uploadMedia()        - Upload file, validate, store, return CDN URL
 *   getMediaByPost()     - Get all media linked to a specific post
 *   getMediaById()       - Get single media item by ID
 *   deleteMedia()        - Soft-delete a media item
 *   softDeleteByPost()   - Soft-delete all media for a deleted post
 *   linkMediaToPost()    - Associate media with a post after creation
 *   getMediaByUploader() - Get all media uploaded by a user
 *
 * Story operations:
 *   createStory()        - Create a 24-hour ephemeral story
 *   getActiveStories()   - Get active stories from a list of followed users
 *   viewStory()          - Increment view count (another user opened the story)
 *   deleteStory()        - Author manually deletes their story
 *   getStoriesByUser()   - Get all stories (active) by a specific user
 *   expireOldStories()   - Called by scheduler to purge expired stories
 */
public interface MediaService {

    // ─── MEDIA OPERATIONS ────────────────────────────────────────────────────

    /**
     * Upload a media file (image or video), validate MIME type and size,
     * simulate CDN storage, and persist the Media record.
     *
     * @param file       Multipart file from the upload request
     * @param uploaderId ID of the authenticated user performing the upload
     * @return MediaResponseDTO containing the CDN URL and media metadata
     */
    ApiResponseDTO<MediaResponseDTO> uploadMedia(MultipartFile file, Integer uploaderId);

    /**
     * Get all active media linked to a specific post.
     * Used when rendering post detail to display attached images/videos.
     *
     * @param postId ID of the post
     * @return List of MediaResponseDTO for the post's media
     */
    ApiResponseDTO<List<MediaResponseDTO>> getMediaByPost(Integer postId);

    /**
     * Get a single media item by its ID.
     *
     * @param mediaId ID of the media item
     * @return MediaResponseDTO with full media details
     */
    ApiResponseDTO<MediaResponseDTO> getMediaById(Integer mediaId);

    /**
     * Soft-delete a media item.
     * Only the uploader can delete their own media.
     * Record is retained for 30-day audit trail (NFR).
     *
     * @param mediaId            ID of the media to delete
     * @param requestingUserId   ID of the authenticated user making the request
     * @param requestingUserRole Role of the user (ADMIN can delete any media)
     * @return Success message
     */
    ApiResponseDTO<String> deleteMedia(Integer mediaId, Integer requestingUserId, String requestingUserRole);

    /**
     * Soft-delete all media linked to a specific post.
     * Called when post-service deletes a post (inter-service).
     * "Media attached to deleted posts is also soft-deleted but retained for audit" (case study 2.6)
     *
     * @param postId ID of the deleted post
     * @return Success message
     */
    ApiResponseDTO<String> softDeleteByPost(Integer postId);

    /**
     * Link an already-uploaded media item to a post.
     * Called after a post is created with pre-uploaded CDN media.
     *
     * @param mediaId  ID of the media to link
     * @param postId   ID of the post to link to
     * @param userId   ID of the user making the request (ownership check)
     * @return Updated MediaResponseDTO
     */
    ApiResponseDTO<MediaResponseDTO> linkMediaToPost(Integer mediaId, Integer postId, Integer userId);

    /**
     * Get all active media uploaded by a specific user.
     *
     * @param uploaderId ID of the user
     * @return List of MediaResponseDTO
     */
    ApiResponseDTO<List<MediaResponseDTO>> getMediaByUploader(Integer uploaderId);

    // ─── STORY OPERATIONS ────────────────────────────────────────────────────

    /**
     * Create a new 24-hour story.
     * Sets expiresAt = createdAt + 24 hours.
     *
     * @param request    Story creation payload (mediaUrl, caption, mediaType)
     * @param authorId   ID of the authenticated user creating the story
     * @return StoryResponseDTO with story details
     */
    ApiResponseDTO<StoryResponseDTO> createStory(CreateStoryRequestDTO request, Integer authorId);

    /**
     * Get all active stories from a list of authorIds (followed users).
     * Used to populate the stories panel in the news feed.
     *
     * @param authorIds List of user IDs whose stories to fetch (from follow-service)
     * @return List of active StoryResponseDTO ordered newest first
     */
    ApiResponseDTO<List<StoryResponseDTO>> getActiveStories(List<Integer> authorIds);

    /**
     * Increment view count for a story (another user opened it).
     * Only increments if the viewer is NOT the author.
     * "Story view counts are incremented when another user opens a story" (case study 2.6)
     *
     * @param storyId         ID of the story being viewed
     * @param viewerUserId    ID of the user who opened the story
     * @return Success message
     */
    ApiResponseDTO<String> viewStory(Integer storyId, Integer viewerUserId);

    /**
     * Author manually deletes their story before it expires.
     * Sets isActive = false.
     *
     * @param storyId          ID of the story to delete
     * @param requestingUserId ID of the authenticated user (must be author or ADMIN)
     * @param requestingUserRole Role of the user
     * @return Success message
     */
    ApiResponseDTO<String> deleteStory(Integer storyId, Integer requestingUserId, String requestingUserRole);

    /**
     * Get all active stories by a specific user.
     * Used for story ring display on the user's profile.
     *
     * @param authorId ID of the user
     * @return List of active StoryResponseDTO
     */
    ApiResponseDTO<List<StoryResponseDTO>> getStoriesByUser(Integer authorId);

    /**
     * Scheduler-triggered method to deactivate all expired stories.
     * "Stories expire exactly 24 hours after creation; a scheduled job purges expired stories" (2.6)
     * NFR: "Stories are purged within 5 minutes of their 24-hour expiry" (section 6)
     *
     * @return Number of stories expired in this batch
     */
    int expireOldStories();
}
