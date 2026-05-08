package com.connectsphere.search.service;

import com.connectsphere.search.client.*;
import com.connectsphere.search.dto.*;
import com.connectsphere.search.entity.*;
import com.connectsphere.search.exception.HashtagNotFoundException;
import com.connectsphere.search.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplBranchTest {

    @Mock private HashtagRepository hashtagRepository;
    @Mock private PostHashtagRepository postHashtagRepository;
    @Mock private PostServiceClient postServiceClient;
    @Mock private AuthServiceClient authServiceClient;

    @InjectMocks private SearchServiceImpl searchService;

    @Test
    void indexPost_skippedBranches() {
        // 1. Null content
        searchService.indexPost(1, 1, null, "PUBLIC");
        // 2. Empty content
        searchService.indexPost(1, 1, "   ", "PUBLIC");
        // 3. Non-PUBLIC visibility
        searchService.indexPost(1, 1, "#tag", "PRIVATE");
        // 4. No hashtags in content
        searchService.indexPost(1, 1, "plain text content", "PUBLIC");
        
        verify(hashtagRepository, never()).save(any());
    }

    @Test
    void reIndexPost_visibilityChangedToNonPublic() {
        // Mock that mappings exist so removePostIndex doesn't return early
        PostHashtag mapping = PostHashtag.builder().hashtag(Hashtag.builder().hashtagId(1).build()).build();
        when(postHashtagRepository.findByPostId(1)).thenReturn(List.of(mapping));

        // If visibility changes to PRIVATE, the index should be removed
        searchService.reIndexPost(1, 1, "content", "old", "PRIVATE");
        
        verify(postHashtagRepository).deleteByPostId(1);
    }

    @Test
    void searchUsers_serviceFailure() {
        // Resilience check: if auth-service is down, return empty list instead of crashing
        when(authServiceClient.searchUsers(anyString())).thenThrow(new RuntimeException("Down"));
        ApiResponseDTO<List<UserSearchResultDTO>> resp = searchService.searchUsers("query");
        assertTrue(resp.isSuccess());
        assertTrue(resp.getData().isEmpty());
    }

    @Test
    void searchPosts_edgeCases() {
        // 1. Empty keyword
        ApiResponseDTO<List<PostSearchResultDTO>> resp1 = searchService.searchPosts("");
        assertFalse(resp1.isSuccess());

        // 2. Keyword starting with #
        when(postHashtagRepository.findPostIdsByHashtagTag("java")).thenReturn(Collections.emptyList());
        searchService.searchPosts("#java");
        verify(postHashtagRepository).findPostIdsByHashtagTag("java");
    }

    @Test
    void getPostsByHashtag_invalidTags() {
        // 1. Empty tag
        ApiResponseDTO<List<PostSearchResultDTO>> resp = searchService.getPostsByHashtag("");
        assertFalse(resp.isSuccess());

        // 2. Hashtag doesn't exist in DB
        when(hashtagRepository.existsByTag(anyString())).thenReturn(false);
        assertThrows(HashtagNotFoundException.class, () -> searchService.getPostsByHashtag("unknown"));
    }

    @Test
    void searchByHashtagInternal_stalePostHandling() {
        // Mock scenario: Hashtag index has postId=1, but post-service says it's deleted
        when(postHashtagRepository.findPostIdsByHashtagTag("tag")).thenReturn(List.of(1));
        
        PostApiResponse deletedRes = new PostApiResponse();
        deletedRes.setSuccess(true);
        PostDataDTO post = new PostDataDTO();
        post.setPostId(1);
        post.setIsDeleted(true); // Deleted in post-service
        deletedRes.setData(post);
        
        when(postServiceClient.getPostById(1)).thenReturn(deletedRes);
        when(hashtagRepository.existsByTag("tag")).thenReturn(true);

        ApiResponseDTO<List<PostSearchResultDTO>> resp = searchService.getPostsByHashtag("tag");
        assertTrue(resp.getData().isEmpty()); // Should filter out the deleted post
    }

    @Test
    void upsertHashtag_existingTag() {
        Hashtag existing = Hashtag.builder().hashtagId(10).tag("java").postCount(5).build();
        when(hashtagRepository.findByTag("java")).thenReturn(Optional.of(existing));
        
        // This triggers the "existing tag" branch (increment instead of save)
        searchService.indexPost(1, 1, "#java", "PUBLIC");
        
        verify(hashtagRepository).incrementPostCount(10);
        verify(hashtagRepository, never()).save(any());
    }

    @Test
    void createPostHashtagMapping_duplicateGuard() {
        Hashtag tag = Hashtag.builder().hashtagId(10).tag("java").build();
        when(hashtagRepository.findByTag("java")).thenReturn(Optional.of(tag));
        // Mock that mapping already exists
        when(postHashtagRepository.existsByPostIdAndHashtagId(1, 10)).thenReturn(true);
        
        searchService.indexPost(1, 1, "#java", "PUBLIC");
        
        verify(postHashtagRepository, never()).save(any());
    }

    @Test
    void getHashtagCount_missingTag() {
        when(hashtagRepository.findPostCountByTag("java")).thenReturn(Optional.empty());
        ApiResponseDTO<Integer> resp = searchService.getHashtagCount("java");
        assertEquals(0, resp.getData());
    }
}
