package com.connectsphere.media.controller;

import com.connectsphere.media.dto.*;
import com.connectsphere.media.entity.Visibility;
import com.connectsphere.media.service.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MediaResourceTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private MediaService mediaService;
    @InjectMocks private MediaResource mediaResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mediaResource).build();
    }

    // ── MEDIA ENDPOINTS ──

    @Test
    void uploadMedia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg",
                "image/jpeg", "data".getBytes());
        when(mediaService.uploadMedia(any(), eq(1)))
                .thenReturn(ApiResponseDTO.success("Uploaded", MediaResponseDTO.builder()
                        .mediaId(1).url("http://cdn/test.jpg").build()));
        mockMvc.perform(multipart("/media/upload").file(file)
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isCreated());
    }

    @Test
    void getMediaById() throws Exception {
        when(mediaService.getMediaById(1))
                .thenReturn(ApiResponseDTO.success("OK", MediaResponseDTO.builder().mediaId(1).build()));
        mockMvc.perform(get("/media/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getMediaByPost() throws Exception {
        when(mediaService.getMediaByPost(1))
                .thenReturn(ApiResponseDTO.success("OK", List.of()));
        mockMvc.perform(get("/media/post/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getMediaByUploader() throws Exception {
        when(mediaService.getMediaByUploader(1))
                .thenReturn(ApiResponseDTO.success("OK", List.of()));
        mockMvc.perform(get("/media/uploader/1"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteMedia() throws Exception {
        when(mediaService.deleteMedia(1, 1, "USER"))
                .thenReturn(ApiResponseDTO.success("Deleted"));
        mockMvc.perform(delete("/media/1")
                        .requestAttr("requestingUserId", 1)
                        .requestAttr("requestingUserRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void linkMediaToPost() throws Exception {
        when(mediaService.linkMediaToPost(1, 2, 1))
                .thenReturn(ApiResponseDTO.success("Linked", MediaResponseDTO.builder().mediaId(1).build()));
        mockMvc.perform(patch("/media/1/link/2")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void softDeleteByPost() throws Exception {
        when(mediaService.softDeleteByPost(1))
                .thenReturn(ApiResponseDTO.success("Deleted"));
        mockMvc.perform(delete("/media/post/1/soft-delete"))
                .andExpect(status().isOk());
    }

    // ── STORY ENDPOINTS ──

    @Test
    void createStory() throws Exception {
        CreateStoryRequestDTO req = CreateStoryRequestDTO.builder()
                .mediaUrl("http://cdn/story.jpg").caption("Test")
                .mediaType(com.connectsphere.media.entity.MediaType.IMAGE)
                .visibility(Visibility.PUBLIC).build();
        when(mediaService.createStory(any(), eq(1)))
                .thenReturn(ApiResponseDTO.success("Created", StoryResponseDTO.builder()
                        .storyId(1).authorId(1).build()));
        mockMvc.perform(post("/media/stories")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isCreated());
    }

    @Test
    void getActiveStories_withAuthorIds() throws Exception {
        when(mediaService.getActiveStories(List.of(1, 2)))
                .thenReturn(ApiResponseDTO.success("OK", List.of()));
        mockMvc.perform(get("/media/stories/feed")
                        .param("authorIds", "1", "2")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void getActiveStories_noAuthorIds() throws Exception {
        when(mediaService.getActiveStoriesForUser(eq(1), any()))
                .thenReturn(ApiResponseDTO.success("OK", List.of()));
        mockMvc.perform(get("/media/stories/feed")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void getAllStories() throws Exception {
        when(mediaService.getAllActiveStories())
                .thenReturn(ApiResponseDTO.success("OK", List.of()));
        mockMvc.perform(get("/media/stories/all"))
                .andExpect(status().isOk());
    }

    @Test
    void viewStory() throws Exception {
        when(mediaService.viewStory(eq(1), eq(1), any()))
                .thenReturn(ApiResponseDTO.success("Viewed"));
        mockMvc.perform(get("/media/stories/1/view")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void getStoriesByUser() throws Exception {
        when(mediaService.getStoriesByUser(1))
                .thenReturn(ApiResponseDTO.success("OK", List.of()));
        mockMvc.perform(get("/media/stories/user/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getStoryViewers() throws Exception {
        when(mediaService.getStoryViewers(1, 1))
                .thenReturn(ApiResponseDTO.success("OK", List.of(Map.of("userId", 2))));
        mockMvc.perform(get("/media/stories/1/viewers")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void deleteStory() throws Exception {
        when(mediaService.deleteStory(1, 1, "USER"))
                .thenReturn(ApiResponseDTO.success("Deleted"));
        mockMvc.perform(delete("/media/stories/1")
                        .requestAttr("requestingUserId", 1)
                        .requestAttr("requestingUserRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteMedia_noRoleAttribute() throws Exception {
        when(mediaService.deleteMedia(1, 1, "USER"))
                .thenReturn(ApiResponseDTO.success("Deleted"));
        mockMvc.perform(delete("/media/1")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void deleteStory_noRoleAttribute() throws Exception {
        when(mediaService.deleteStory(1, 1, "USER"))
                .thenReturn(ApiResponseDTO.success("Deleted"));
        mockMvc.perform(delete("/media/stories/1")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void serveFile_fileExists() throws Exception {
        // Create a temp file in the storageBasePath
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("media-test");
        java.nio.file.Path tempFile = tempDir.resolve("test-file.txt");
        java.nio.file.Files.writeString(tempFile, "hello");

        // Use reflection to set storageBasePath
        org.springframework.test.util.ReflectionTestUtils.setField(
                mediaResource, "storageBasePath", tempDir.toString());

        // Rebuild MockMvc after setting the field
        mockMvc = MockMvcBuilders.standaloneSetup(mediaResource).build();

        mockMvc.perform(get("/media/cdn/test-file.txt"))
                .andExpect(status().isOk());

        // Cleanup
        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void serveFile_fileNotFound() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("media-test-empty");

        org.springframework.test.util.ReflectionTestUtils.setField(
                mediaResource, "storageBasePath", tempDir.toString());
        mockMvc = MockMvcBuilders.standaloneSetup(mediaResource).build();

        mockMvc.perform(get("/media/cdn/nonexistent.jpg"))
                .andExpect(status().isNotFound());

        java.nio.file.Files.deleteIfExists(tempDir);
    }
}

