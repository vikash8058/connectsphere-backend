package com.connectsphere.comment.controller;

import com.connectsphere.comment.dto.*;
import com.connectsphere.comment.service.CommentService;
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

@WebMvcTest(CommentResource.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CommentResource Controller — MockMvc Tests")
class CommentResourceTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private CommentService commentService;
    @MockBean
    private com.connectsphere.comment.security.GatewayHeaderFilter gatewayHeaderFilter;

    @Test
    @DisplayName("GET /comments/post/{postId} → 200 OK")
    void getCommentsByPost() throws Exception {
        when(commentService.getCommentsByPost(1))
                .thenReturn(ApiResponseDTO.<List<CommentResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/comments/post/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /comments/post/{postId}/top-level → 200 OK")
    void getTopLevelComments() throws Exception {
        when(commentService.getTopLevelComments(1))
                .thenReturn(ApiResponseDTO.<List<CommentResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/comments/post/1/top-level"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /comments/{commentId} → 200 OK")
    void getCommentById() throws Exception {
        when(commentService.getCommentById(1))
                .thenReturn(ApiResponseDTO.<CommentResponseDTO>builder().success(true).build());
        mockMvc.perform(get("/comments/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /comments/{commentId}/replies → 200 OK")
    void getReplies() throws Exception {
        when(commentService.getReplies(1))
                .thenReturn(ApiResponseDTO.<List<CommentResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/comments/1/replies"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /comments/count/{postId} → 200 OK")
    void getCommentCount() throws Exception {
        when(commentService.getCommentCount(1))
                .thenReturn(ApiResponseDTO.<Integer>builder().success(true).data(10).build());
        mockMvc.perform(get("/comments/count/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /comments → 201 Created")
    void addComment() throws Exception {
        AddCommentRequestDTO req = AddCommentRequestDTO.builder().postId(1).content("Hello").build();
        when(commentService.addComment(anyInt(), any()))
                .thenReturn(ApiResponseDTO.<CommentResponseDTO>builder().success(true).build());

        mockMvc.perform(post("/comments")
                .requestAttr("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /comments/{commentId} → 200 OK")
    void updateComment() throws Exception {
        UpdateCommentRequestDTO req = UpdateCommentRequestDTO.builder().content("Updated").build();
        when(commentService.updateComment(anyInt(), anyInt(), any()))
                .thenReturn(ApiResponseDTO.<CommentResponseDTO>builder().success(true).build());

        mockMvc.perform(put("/comments/1")
                .requestAttr("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /comments/{commentId} → 200 OK")
    void deleteComment() throws Exception {
        when(commentService.deleteComment(anyInt(), anyInt(), anyString()))
                .thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(delete("/comments/1")
                .requestAttr("requestingUserId", 1)
                .requestAttr("requestingUserRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /comments/{commentId}/like → 200 OK")
    void likeComment() throws Exception {
        when(commentService.likeComment(1)).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/comments/1/like"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /comments/{commentId}/unlike → 200 OK")
    void unlikeComment() throws Exception {
        when(commentService.unlikeComment(1)).thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/comments/1/unlike"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /comments/user/{authorId} → 200 OK")
    void getCommentsByUser() throws Exception {
        when(commentService.getCommentsByUser(1))
                .thenReturn(ApiResponseDTO.<List<CommentResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/comments/user/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /comments without userId → RuntimeException")
    void addComment_unauthorized() throws Exception {
        AddCommentRequestDTO req = AddCommentRequestDTO.builder().postId(1).content("Hi").build();

        // We DON'T set the "requestingUserId" attribute
        mockMvc.perform(post("/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError()); // Triggers GlobalExceptionHandler
    }

}
