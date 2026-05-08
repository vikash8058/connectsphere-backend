package com.connectsphere.follow.controller;

import com.connectsphere.follow.dto.*;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.service.FollowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FollowResourceTest {

    private MockMvc mockMvc;

    @Mock private FollowService followService;
    @InjectMocks private FollowResource followResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(followResource).build();
    }

    @Test
    void follow() throws Exception {
        when(followService.follow(1, 2))
                .thenReturn(ApiResponseDTO.success("Followed", FollowResponseDTO.builder()
                        .followId(1).followerId(1).followeeId(2).status(FollowStatus.ACTIVE).build()));
        mockMvc.perform(post("/follows/2").requestAttr("requestingUserId", 1))
                .andExpect(status().isCreated());
    }

    @Test
    void unfollow() throws Exception {
        when(followService.unfollow(1, 2))
                .thenReturn(ApiResponseDTO.success("Unfollowed"));
        mockMvc.perform(delete("/follows/2").requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void isFollowing() throws Exception {
        when(followService.isFollowing(1, 2))
                .thenReturn(ApiResponseDTO.success("OK", true));
        mockMvc.perform(get("/follows/check/2").requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void getSuggestedUsers() throws Exception {
        when(followService.getSuggestedUsers(1))
                .thenReturn(ApiResponseDTO.success("OK", List.of(3, 4)));
        mockMvc.perform(get("/follows/suggestions").requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void getFollowers() throws Exception {
        when(followService.getFollowers(1))
                .thenReturn(ApiResponseDTO.success("OK", List.of()));
        mockMvc.perform(get("/follows/1/followers"))
                .andExpect(status().isOk());
    }

    @Test
    void getFollowing() throws Exception {
        when(followService.getFollowing(1))
                .thenReturn(ApiResponseDTO.success("OK", List.of()));
        mockMvc.perform(get("/follows/1/following"))
                .andExpect(status().isOk());
    }

    @Test
    void getFollowerCount() throws Exception {
        when(followService.getFollowerCount(1))
                .thenReturn(ApiResponseDTO.success("OK", 5));
        mockMvc.perform(get("/follows/1/follower-count"))
                .andExpect(status().isOk());
    }

    @Test
    void getFollowingCount() throws Exception {
        when(followService.getFollowingCount(1))
                .thenReturn(ApiResponseDTO.success("OK", 3));
        mockMvc.perform(get("/follows/1/following-count"))
                .andExpect(status().isOk());
    }

    @Test
    void getFollowCounts() throws Exception {
        when(followService.getFollowCounts(1))
                .thenReturn(ApiResponseDTO.success("OK", FollowCountDTO.builder()
                        .userId(1).followerCount(5).followingCount(3).build()));
        mockMvc.perform(get("/follows/1/counts"))
                .andExpect(status().isOk());
    }

    @Test
    void getMutualFollows() throws Exception {
        when(followService.getMutualFollows(1))
                .thenReturn(ApiResponseDTO.success("OK", List.of(2)));
        mockMvc.perform(get("/follows/1/mutual"))
                .andExpect(status().isOk());
    }

    @Test
    void getFolloweeIds() throws Exception {
        when(followService.getFolloweeIds(1))
                .thenReturn(ApiResponseDTO.success("OK", List.of(2, 3)));
        mockMvc.perform(get("/follows/1/followee-ids"))
                .andExpect(status().isOk());
    }
}
