package com.connectsphere.media.service;

import com.connectsphere.media.client.*;
import com.connectsphere.media.dto.*;
import com.connectsphere.media.entity.*;
import com.connectsphere.media.exception.*;
import com.connectsphere.media.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplBranchTest {

    @Mock private MediaRepository mediaRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private StoryViewRepository storyViewRepository;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private FollowServiceClient followServiceClient;
    @Mock private PostServiceClient postServiceClient;

    @InjectMocks private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "cdnBaseUrl", "http://cdn");
        ReflectionTestUtils.setField(mediaService, "maxImageSizeKb", 1024L);
        ReflectionTestUtils.setField(mediaService, "maxVideoSizeKb", 1024L);
        ReflectionTestUtils.setField(mediaService, "allowedImageTypes", "image/jpeg,image/png");
        ReflectionTestUtils.setField(mediaService, "allowedVideoTypes", "video/mp4");
        ReflectionTestUtils.setField(mediaService, "storageBasePath", "uploads");
    }

    @Test
    void uploadMedia_nullMimeType() {
        MockMultipartFile file = new MockMultipartFile("file", "test", null, "data".getBytes());
        assertThrows(UnsupportedMediaTypeException.class, () -> mediaService.uploadMedia(file, 1));
    }

    @Test
    void uploadMedia_extensionResolution() {
        // Test file with no extension but valid mime
        MockMultipartFile file = new MockMultipartFile("file", "noextension", "image/jpeg", "data".getBytes());
        when(mediaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ApiResponseDTO<MediaResponseDTO> resp = mediaService.uploadMedia(file, 1);
        assertTrue(resp.getData().getUrl().contains(".jpg")); 
    }

    @Test
    void softDeleteByPost_clientFailure() {
        // Test Resilience: Post service returns error
        when(postServiceClient.getPostById(1)).thenReturn(ApiResponseDTO.error("Fail"));
        assertThrows(PostNotFoundException.class, () -> mediaService.softDeleteByPost(1));
    }

    @Test
    void getActiveStoriesForUser_followServiceFailure() {
        // Test Resilience: Follow service is down
        when(followServiceClient.getFolloweeIds(1)).thenReturn(ApiResponseDTO.error("Down"));
        ApiResponseDTO<List<StoryResponseDTO>> resp = mediaService.getActiveStoriesForUser(1, "token");
        // Should fallback to success with empty list or just own stories
        assertTrue(resp.isSuccess());
    }

    @Test
    void viewStory_privateAccessDenied() {
        Story story = Story.builder().storyId(1).authorId(2).visibility(Visibility.PRIVATE).isActive(true).build();
        when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));
        assertThrows(UnauthorizedActionException.class, () -> mediaService.viewStory(1, 3, "token"));
    }

    @Test
    void viewStory_followersOnlyAccessDenied() {
        Story story = Story.builder().storyId(1).authorId(2).visibility(Visibility.FOLLOWERS_ONLY).isActive(true).build();
        when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));
        // Mock follow check as false
        when(followServiceClient.isFollowing(eq(2), anyString())).thenReturn(ApiResponseDTO.success("OK", false));
        assertThrows(UnauthorizedActionException.class, () -> mediaService.viewStory(1, 3, "token"));
    }

    @Test
    void viewStory_followersOnlyAccessAllowed() {
        Story story = Story.builder().storyId(1).authorId(2).visibility(Visibility.FOLLOWERS_ONLY).isActive(true).build();
        when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));
        when(followServiceClient.isFollowing(eq(2), anyString())).thenReturn(ApiResponseDTO.success("OK", true));
        
        ApiResponseDTO<String> resp = mediaService.viewStory(1, 3, "token");
        assertTrue(resp.isSuccess());
    }

    @Test
    void getStoryViewers_unauthorized() {
        Story story = Story.builder().storyId(1).authorId(2).build();
        when(storyRepository.findById(1)).thenReturn(Optional.of(story));
        assertThrows(UnauthorizedActionException.class, () -> mediaService.getStoryViewers(1, 3));
    }

    @Test
    void getStoryViewers_authServicePartialFailure() {
        Story story = Story.builder().storyId(1).authorId(2).build();
        when(storyRepository.findById(1)).thenReturn(Optional.of(story));
        
        when(storyViewRepository.findViewerUserIdsByStoryId(1)).thenReturn(List.of(5));
        
        // Mock auth service failure for this user by throwing exception to hit catch block
        when(authServiceClient.getUserById(5)).thenThrow(new RuntimeException("Service down"));
        
        ApiResponseDTO<List<Map<String, Object>>> resp = mediaService.getStoryViewers(1, 2);
        assertTrue(resp.isSuccess());
        assertEquals("Unknown User", resp.getData().get(0).get("username"));
    }
}
