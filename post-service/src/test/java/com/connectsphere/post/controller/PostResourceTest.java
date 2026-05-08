package com.connectsphere.post.controller;

import com.connectsphere.post.dto.*;
import com.connectsphere.post.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostResource.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PostResource Controller — MockMvc Tests")
class PostResourceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PostService postService;
    @MockBean private com.connectsphere.post.security.GatewayHeaderFilter gatewayHeaderFilter;

    @Test
    @DisplayName("GET /posts/public → 200 OK")
    void getPublicPosts_success() throws Exception {
        when(postService.getPublicFeed()).thenReturn(ApiResponseDTO.<List<PostResponseDTO>>builder().success(true).data(List.of()).build());
        mockMvc.perform(get("/posts/public"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /posts/{id} → 200 OK")
    void getPostById_success() throws Exception {
        when(postService.getPostById(1)).thenReturn(ApiResponseDTO.<PostResponseDTO>builder().success(true).build());
        mockMvc.perform(get("/posts/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /posts → 201 Created")
    void createPost_success() throws Exception {
        CreatePostRequestDTO req = CreatePostRequestDTO.builder()
                .content("Hello World")
                .build();
        when(postService.createPost(anyInt(), any())).thenReturn(ApiResponseDTO.<PostResponseDTO>builder().success(true).build());
        
        mockMvc.perform(post("/posts")
                        .requestAttr("requestingUserId", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /posts (Validation Failure) → 400 Bad Request")
    void createPost_validationError() throws Exception {
        // Create a request that fails @Size(max=5000) - wait, let's use something that fails validation
        // The CreatePostRequestDTO doesn't have @NotBlank, only @Size(max=5000).
        // Let's check the DTO again.
        // If I can't trigger it via @Valid, I'll trigger it by mocking the service to throw MethodArgumentNotValidException?
        // No, that's not how it works.
        // Let's just send a null content if it was @NotNull.
        // Actually, let's just test the handler separately in GlobalExceptionHandlerTest.
        
        CreatePostRequestDTO req = CreatePostRequestDTO.builder().content("a".repeat(5001)).build();
        mockMvc.perform(post("/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /posts/{id} → 200 OK")
    void updatePost_success() throws Exception {
        UpdatePostRequestDTO req = UpdatePostRequestDTO.builder()
                .content("Updated")
                .build();
        when(postService.updatePost(anyInt(), anyInt(), any())).thenReturn(ApiResponseDTO.<PostResponseDTO>builder().success(true).build());
        
        mockMvc.perform(put("/posts/1")
                        .requestAttr("requestingUserId", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /posts/{id} → 200 OK")
    void deletePost_success() throws Exception {
        when(postService.deletePost(anyInt(), anyInt(), anyString())).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(delete("/posts/1")
                        .requestAttr("requestingUserId", 1)
                        .requestAttr("requestingUserRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /posts/feed → 200 OK")
    void getFeed_success() throws Exception {
        when(postService.getFeedForUser(anyInt(), anyString())).thenReturn(ApiResponseDTO.<List<PostResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/posts/feed")
                        .requestAttr("requestingUserId", 1)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /posts/search → 200 OK")
    void searchPosts_success() throws Exception {
        when(postService.searchPosts(anyString())).thenReturn(ApiResponseDTO.<List<PostResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/posts/search").param("keyword", "test"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /posts/count/{id} → 200 OK")
    void getPostCount_success() throws Exception {
        when(postService.getPostCount(1)).thenReturn(ApiResponseDTO.<Integer>builder().success(true).data(5).build());
        mockMvc.perform(get("/posts/count/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /posts/{id}/visibility → 200 OK")
    void changeVisibility_success() throws Exception {
        when(postService.changeVisibility(anyInt(), anyInt(), anyString())).thenReturn(ApiResponseDTO.<PostResponseDTO>builder().success(true).build());
        mockMvc.perform(patch("/posts/1/visibility")
                        .param("visibility", "PRIVATE")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /posts/{id}/likes/increment → 200 OK")
    void incrementLikes_success() throws Exception {
        when(postService.incrementLikes(1)).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/posts/1/likes/increment"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("POST /posts/{id}/comments/increment → 200 OK")
    void incrementComments_success() throws Exception {
        when(postService.incrementComments(1)).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/posts/1/comments/increment"))
                .andExpect(status().isOk());
    }
}
