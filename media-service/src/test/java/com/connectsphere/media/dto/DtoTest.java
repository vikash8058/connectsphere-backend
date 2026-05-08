package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import com.connectsphere.media.entity.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void testMediaResponseDTO() {
        MediaResponseDTO dto = MediaResponseDTO.builder()
                .mediaId(1).uploaderId(2).url("http://cdn/img.jpg")
                .mediaType(MediaType.IMAGE).sizeKb(500L).mimeType("image/jpeg")
                .linkedPostId(10).uploadedAt(LocalDateTime.now()).build();
        assertEquals(1, dto.getMediaId());
        assertEquals("image/jpeg", dto.getMimeType());
    }

    @Test
    void testStoryResponseDTO() {
        StoryResponseDTO dto = new StoryResponseDTO();
        dto.setStoryId(1);
        dto.setAuthorId(2);
        dto.setMediaUrl("http://cdn/story.jpg");
        dto.setCaption("Caption");
        dto.setMediaType(MediaType.IMAGE);
        dto.setViewsCount(10);
        dto.setAuthorUsername("john");
        dto.setAuthorProfilePic("pic.jpg");
        dto.setExpiresAt(LocalDateTime.now());
        dto.setCreatedAt(LocalDateTime.now());
        dto.setVisibility(Visibility.PUBLIC);
        dto.setIsActive(true);
        assertEquals(1, dto.getStoryId());
        assertEquals("john", dto.getAuthorUsername());
    }

    @Test
    void testCreateStoryRequestDTO() {
        CreateStoryRequestDTO dto = CreateStoryRequestDTO.builder()
                .mediaUrl("http://cdn/story.jpg").caption("Cap")
                .mediaType(MediaType.IMAGE).visibility(Visibility.FOLLOWERS_ONLY).build();
        assertEquals("http://cdn/story.jpg", dto.getMediaUrl());
        assertEquals(Visibility.FOLLOWERS_ONLY, dto.getVisibility());
    }

    @Test
    void testPostSummaryDTO() {
        PostSummaryDTO dto = new PostSummaryDTO();
        dto.setPostId(1);
        dto.setAuthorId(2);
        assertEquals(1, dto.getPostId());
        assertEquals(2, dto.getAuthorId());
    }

    @Test
    void testApiResponseDTO() {
        ApiResponseDTO<String> s = ApiResponseDTO.success("OK", "data");
        assertTrue(s.isSuccess());
        assertEquals("data", s.getData());

        ApiResponseDTO<String> s2 = ApiResponseDTO.success("OK");
        assertTrue(s2.isSuccess());
        assertNull(s2.getData());

        ApiResponseDTO<String> e = ApiResponseDTO.error("Fail");
        assertFalse(e.isSuccess());
    }

    @Test
    void testStoryViewEntity() {
        Story story = Story.builder()
                .storyId(1).authorId(2).mediaUrl("http://cdn/s.jpg")
                .mediaType(MediaType.IMAGE).viewsCount(0)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .visibility(Visibility.PUBLIC).isActive(true).build();

        StoryView sv = new StoryView();
        sv.setId(1L);
        sv.setStory(story);
        sv.setViewerUserId(5);
        sv.setViewedAt(LocalDateTime.now());
        assertEquals(1L, sv.getId());
        assertEquals(story, sv.getStory());
        assertEquals(5, sv.getViewerUserId());
        assertNotNull(sv.getViewedAt());
    }

    @Test
    void testMediaEntity() {
        Media media = new Media();
        media.setMediaId(1);
        media.setUploaderId(2);
        media.setUrl("http://cdn/img.jpg");
        media.setMediaType(MediaType.IMAGE);
        media.setSizeKb(500L);
        media.setMimeType("image/jpeg");
        media.setLinkedPostId(10);
        media.setIsDeleted(false);
        media.setUploadedAt(LocalDateTime.now());
        assertEquals(1, media.getMediaId());
        assertFalse(media.getIsDeleted());
    }

    @Test
    void testStoryEntity() {
        Story story = new Story();
        story.setStoryId(1);
        story.setAuthorId(2);
        story.setMediaUrl("url");
        story.setCaption("cap");
        story.setMediaType(MediaType.VIDEO);
        story.setViewsCount(5);
        story.setExpiresAt(LocalDateTime.now());
        story.setCreatedAt(LocalDateTime.now());
        story.setIsActive(true);
        story.setVisibility(Visibility.PRIVATE);
        assertEquals(1, story.getStoryId());
        assertEquals(Visibility.PRIVATE, story.getVisibility());
    }

    @Test
    void testMediaResponseDTO_setters() {
        MediaResponseDTO dto = new MediaResponseDTO();
        dto.setMediaId(1);
        dto.setUploaderId(2);
        dto.setUrl("url");
        dto.setMediaType(MediaType.IMAGE);
        dto.setSizeKb(100L);
        dto.setMimeType("image/png");
        dto.setLinkedPostId(5);
        dto.setUploadedAt(LocalDateTime.now());
        assertEquals(2, dto.getUploaderId());
        assertEquals("image/png", dto.getMimeType());
    }
}
