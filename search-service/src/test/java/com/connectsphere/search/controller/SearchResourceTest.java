package com.connectsphere.search.controller;

import com.connectsphere.search.dto.*;
import com.connectsphere.search.service.SearchService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchResourceTest {

    private MockMvc mockMvc;

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchResource searchResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchResource).build();
    }

    @Test
    void searchPosts() throws Exception {
        when(searchService.searchPosts(anyString()))
                .thenReturn(ApiResponseDTO.success("OK", List.of(PostSearchResultDTO.builder().postId(1).build())));

        mockMvc.perform(get("/search/posts")
                        .param("keyword", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].postId").value(1));
    }

    @Test
    void searchUsers() throws Exception {
        when(searchService.searchUsers(anyString()))
                .thenReturn(ApiResponseDTO.success("OK", List.of(UserSearchResultDTO.builder().userId(1).username("user").build())));

        mockMvc.perform(get("/search/users")
                        .param("query", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(1));
    }

    @Test
    void getTrendingHashtags() throws Exception {
        when(searchService.getTrendingHashtags(anyInt()))
                .thenReturn(ApiResponseDTO.success("OK", List.of(HashtagResponseDTO.builder().tag("trending").build())));

        mockMvc.perform(get("/hashtags/trending")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tag").value("trending"));
    }

    @Test
    void getPostsByHashtag() throws Exception {
        when(searchService.getPostsByHashtag(anyString()))
                .thenReturn(ApiResponseDTO.success("OK", List.of(PostSearchResultDTO.builder().postId(2).build())));

        mockMvc.perform(get("/hashtags/java/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].postId").value(2));
    }

    @Test
    void getHashtagsForPost() throws Exception {
        when(searchService.getHashtagsForPost(anyInt()))
                .thenReturn(ApiResponseDTO.success("OK", List.of(HashtagResponseDTO.builder().tag("test").build())));

        mockMvc.perform(get("/hashtags/post/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tag").value("test"));
    }

    @Test
    void searchHashtags() throws Exception {
        when(searchService.searchHashtags(anyString()))
                .thenReturn(ApiResponseDTO.success("OK", List.of(HashtagResponseDTO.builder().tag("java").build())));

        mockMvc.perform(get("/hashtags/search")
                        .param("query", "ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tag").value("java"));
    }

    @Test
    void getHashtagCount() throws Exception {
        when(searchService.getHashtagCount(anyString()))
                .thenReturn(ApiResponseDTO.success("OK", 42));

        mockMvc.perform(get("/hashtags/java/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
    }
}
