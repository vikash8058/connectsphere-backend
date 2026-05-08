package com.connectsphere.like.controller;

import com.connectsphere.like.dto.*;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.service.LikeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LikeResource Controller Tests")
class LikeResourceTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private LikeService likeService;
    @InjectMocks
    private LikeResource likeResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(likeResource).build();
    }

    @Test
    @DisplayName("POST /likes → 201 Created")
    void likeTarget() throws Exception {
        LikeRequestDTO req = LikeRequestDTO.builder()
                .targetId(1).targetType(TargetType.POST).reactionType(ReactionType.LIKE).build();
        when(likeService.likeTarget(anyInt(), any()))
                .thenReturn(ApiResponseDTO.<LikeResponseDTO>builder().success(true).build());

        mockMvc.perform(post("/likes")
                        .requestAttr("requestingUserId", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DELETE /likes → 200 OK")
    void unlikeTarget() throws Exception {
        when(likeService.unlikeTarget(anyInt(), anyInt(), any()))
                .thenReturn(ApiResponseDTO.<String>builder().success(true).build());

        mockMvc.perform(delete("/likes")
                        .requestAttr("requestingUserId", 1)
                        .param("targetId", "1")
                        .param("targetType", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /likes/change → 200 OK")
    void changeReaction() throws Exception {
        ChangeReactionRequestDTO req = ChangeReactionRequestDTO.builder()
                .targetId(1).targetType(TargetType.POST).newReactionType(ReactionType.LOVE).build();
        when(likeService.changeReaction(anyInt(), any()))
                .thenReturn(ApiResponseDTO.<LikeResponseDTO>builder().success(true).build());

        mockMvc.perform(put("/likes/change")
                        .requestAttr("requestingUserId", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /likes/my → 200 OK")
    void getMyReaction() throws Exception {
        when(likeService.getUserReaction(anyInt(), anyInt(), any()))
                .thenReturn(ApiResponseDTO.<LikeResponseDTO>builder().success(true).build());

        mockMvc.perform(get("/likes/my")
                        .requestAttr("requestingUserId", 1)
                        .param("targetId", "1")
                        .param("targetType", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /likes/has → 200 OK")
    void hasLiked() throws Exception {
        when(likeService.hasLiked(anyInt(), anyInt(), any()))
                .thenReturn(ApiResponseDTO.<Boolean>builder().success(true).data(true).build());

        mockMvc.perform(get("/likes/has")
                        .requestAttr("requestingUserId", 1)
                        .param("targetId", "1")
                        .param("targetType", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /likes/target → 200 OK")
    void getLikesByTarget() throws Exception {
        when(likeService.getLikesByTarget(anyInt(), any()))
                .thenReturn(ApiResponseDTO.<List<LikeResponseDTO>>builder().success(true).build());

        mockMvc.perform(get("/likes/target")
                        .param("targetId", "1")
                        .param("targetType", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /likes/user/{userId} → 200 OK")
    void getLikesByUser() throws Exception {
        when(likeService.getLikesByUser(1))
                .thenReturn(ApiResponseDTO.<List<LikeResponseDTO>>builder().success(true).build());

        mockMvc.perform(get("/likes/user/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /likes/count → 200 OK")
    void getLikeCount() throws Exception {
        when(likeService.getLikeCount(anyInt(), any()))
                .thenReturn(ApiResponseDTO.<Integer>builder().success(true).data(5).build());

        mockMvc.perform(get("/likes/count")
                        .param("targetId", "1")
                        .param("targetType", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /likes/count/type → 200 OK")
    void getLikeCountByType() throws Exception {
        when(likeService.getLikeCountByType(anyInt(), any(), anyString()))
                .thenReturn(ApiResponseDTO.<Integer>builder().success(true).data(3).build());

        mockMvc.perform(get("/likes/count/type")
                        .param("targetId", "1")
                        .param("targetType", "POST")
                        .param("reactionType", "LIKE"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /likes/summary → 200 OK")
    void getReactionSummary() throws Exception {
        when(likeService.getReactionSummary(anyInt(), any()))
                .thenReturn(ApiResponseDTO.<ReactionSummaryDTO>builder().success(true).build());

        mockMvc.perform(get("/likes/summary")
                        .param("targetId", "1")
                        .param("targetType", "POST"))
                .andExpect(status().isOk());
    }
}
