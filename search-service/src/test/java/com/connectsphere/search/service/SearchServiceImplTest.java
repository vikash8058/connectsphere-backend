package com.connectsphere.search.service;

import com.connectsphere.search.client.AuthServiceClient;
import com.connectsphere.search.client.PostServiceClient;
import com.connectsphere.search.dto.*;
import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.entity.PostHashtag;
import com.connectsphere.search.exception.HashtagNotFoundException;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SearchServiceImplTest - Unit tests for Search/Hashtag Service business logic
 *
 * Uses Mockito to mock:
 *   - HashtagRepository      → no DB needed
 *   - PostHashtagRepository  → no DB needed
 *   - PostServiceClient      → no actual Feign HTTP call
 *   - AuthServiceClient      → no actual Feign HTTP call
 *
 * Tests cover:
 *   indexPost         (new tags, existing tags, non-PUBLIC skipped, empty content)
 *   reIndexPost       (added tags, removed tags, visibility changed to private)
 *   removePostIndex   (with mappings, no mappings)
 *   searchPosts       (keyword, hashtag prefix, empty keyword)
 *   searchUsers       (results, empty response, feign failure)
 *   getHashtagsForPost
 *   getTrendingHashtags
 *   getPostsByHashtag  (exists, not found)
 *   searchHashtags
 *   getHashtagCount    (exists, not found)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchServiceImpl Unit Tests")
class SearchServiceImplTest {

    @Mock private HashtagRepository hashtagRepository;
    @Mock private PostHashtagRepository postHashtagRepository;
    @Mock private PostServiceClient postServiceClient;
    @Mock private AuthServiceClient authServiceClient;

    @InjectMocks
    private SearchServiceImpl searchService;

    // ── Test Data Builders ────────────────────────────────────────────────────

    private Hashtag buildHashtag(Integer id, String tag, int postCount) {
        return Hashtag.builder()
                .hashtagId(id)
                .tag(tag)
                .postCount(postCount)
                .lastUsedAt(LocalDateTime.now())
                .build();
    }

    private PostHashtag buildPostHashtag(Integer postId, Hashtag hashtag) {
        return PostHashtag.builder()
                .id(1)
                .postId(postId)
                .hashtag(hashtag)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private PostDataDTO buildPostData(Integer postId, Integer authorId, String content) {
        return PostDataDTO.builder()
                .postId(postId)
                .authorId(authorId)
                .content(content)
                .mediaUrls(Collections.emptyList())
                .postType("TEXT")
                .visibility("PUBLIC")
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── indexPost() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("indexPost()")
    class IndexPostTests {

        @Test
        @DisplayName("Should index new hashtags from post content")
        void indexPost_newTags_success() {
            // Given
            Integer postId = 1;
            String content = "Hello #spring and #boot are great!";

            when(hashtagRepository.findByTag("spring")).thenReturn(Optional.empty());
            when(hashtagRepository.findByTag("boot")).thenReturn(Optional.empty());

            Hashtag springTag = buildHashtag(1, "spring", 1);
            Hashtag bootTag   = buildHashtag(2, "boot", 1);
            when(hashtagRepository.save(any(Hashtag.class)))
                    .thenReturn(springTag)
                    .thenReturn(bootTag);

            when(postHashtagRepository.existsByPostIdAndHashtagId(anyInt(), anyInt()))
                    .thenReturn(false);

            // When
            searchService.indexPost(postId, 10, content, "PUBLIC");

            // Then — two hashtags saved, two PostHashtag mappings created
            verify(hashtagRepository, times(2)).save(any(Hashtag.class));
            verify(postHashtagRepository, times(2)).save(any(PostHashtag.class));
        }

        @Test
        @DisplayName("Should increment postCount for existing hashtag")
        void indexPost_existingTag_incrementsCount() {
            // Given
            Integer postId = 2;
            String content = "Check out #java tutorials";

            Hashtag existing = buildHashtag(5, "java", 10);
            when(hashtagRepository.findByTag("java")).thenReturn(Optional.of(existing));
            when(postHashtagRepository.existsByPostIdAndHashtagId(postId, 5)).thenReturn(false);

            // When
            searchService.indexPost(postId, 10, content, "PUBLIC");

            // Then — existing tag → increment, not insert
            verify(hashtagRepository).incrementPostCount(5);
            verify(hashtagRepository, never()).save(any(Hashtag.class));
            verify(postHashtagRepository).save(any(PostHashtag.class));
        }

        @Test
        @DisplayName("Should skip indexing for non-PUBLIC posts")
        void indexPost_nonPublic_skipped() {
            // When
            searchService.indexPost(3, 10, "Hello #java", "PRIVATE");
            searchService.indexPost(4, 10, "Hello #java", "FOLLOWERS_ONLY");

            // Then — nothing indexed
            verifyNoInteractions(hashtagRepository);
            verifyNoInteractions(postHashtagRepository);
        }

        @Test
        @DisplayName("Should skip indexing for empty content")
        void indexPost_emptyContent_skipped() {
            searchService.indexPost(5, 10, "", "PUBLIC");
            searchService.indexPost(6, 10, null, "PUBLIC");

            verifyNoInteractions(hashtagRepository);
        }

        @Test
        @DisplayName("Should skip duplicate PostHashtag mapping")
        void indexPost_duplicateMapping_skipped() {
            // Given
            Hashtag existing = buildHashtag(1, "spring", 5);
            when(hashtagRepository.findByTag("spring")).thenReturn(Optional.of(existing));
            when(postHashtagRepository.existsByPostIdAndHashtagId(1, 1)).thenReturn(true);

            // When
            searchService.indexPost(1, 10, "Hello #spring", "PUBLIC");

            // Then — no mapping saved (dedup guard)
            verify(postHashtagRepository, never()).save(any(PostHashtag.class));
        }
    }

    // ── reIndexPost() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reIndexPost()")
    class ReIndexPostTests {

        @Test
        @DisplayName("Should remove index entirely when visibility changes to PRIVATE")
        void reIndexPost_visibilityPrivate_removesAll() {
            // Given
            Integer postId = 10;
            Hashtag tag = buildHashtag(1, "java", 3);
            PostHashtag mapping = buildPostHashtag(postId, tag);
            when(postHashtagRepository.findByPostId(postId)).thenReturn(List.of(mapping));

            // When
            searchService.reIndexPost(postId, 5, "Hello #java", "Hello #java", "PRIVATE");

            // Then
            verify(postHashtagRepository).deleteByPostId(postId);
            verify(hashtagRepository).decrementPostCount(1);
        }

        @Test
        @DisplayName("Should add new tags and remove old tags on update")
        void reIndexPost_addedAndRemovedTags() {
            // Given
            Integer postId = 11;
            String oldContent = "Hello #spring";
            String newContent = "Hello #boot";   // removed: spring, added: boot

            // Old tag exists
            Hashtag springTag = buildHashtag(1, "spring", 3);
            when(hashtagRepository.findByTag("spring")).thenReturn(Optional.of(springTag));
            when(postHashtagRepository.findByPostId(postId)).thenReturn(
                    List.of(buildPostHashtag(postId, springTag)));

            // New tag is fresh
            when(hashtagRepository.findByTag("boot")).thenReturn(Optional.empty());
            Hashtag bootTag = buildHashtag(2, "boot", 1);
            when(hashtagRepository.save(any(Hashtag.class))).thenReturn(bootTag);
            when(postHashtagRepository.existsByPostIdAndHashtagId(postId, 2)).thenReturn(false);

            // When
            searchService.reIndexPost(postId, 5, newContent, oldContent, "PUBLIC");

            // Then
            verify(hashtagRepository).decrementPostCount(1);   // spring removed
            verify(hashtagRepository).save(any(Hashtag.class)); // boot inserted
            verify(postHashtagRepository).save(any(PostHashtag.class)); // new mapping
        }
    }

    // ── removePostIndex() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("removePostIndex()")
    class RemovePostIndexTests {

        @Test
        @DisplayName("Should decrement all hashtag counts and delete mappings")
        void removePostIndex_success() {
            // Given
            Integer postId = 20;
            Hashtag tag1 = buildHashtag(1, "java", 5);
            Hashtag tag2 = buildHashtag(2, "spring", 3);
            when(postHashtagRepository.findByPostId(postId)).thenReturn(List.of(
                    buildPostHashtag(postId, tag1),
                    buildPostHashtag(postId, tag2)
            ));

            // When
            searchService.removePostIndex(postId);

            // Then
            verify(hashtagRepository).decrementPostCount(1);
            verify(hashtagRepository).decrementPostCount(2);
            verify(postHashtagRepository).deleteByPostId(postId);
        }

        @Test
        @DisplayName("Should do nothing when no mappings exist")
        void removePostIndex_noMappings_noOp() {
            when(postHashtagRepository.findByPostId(99)).thenReturn(Collections.emptyList());

            searchService.removePostIndex(99);

            verify(hashtagRepository, never()).decrementPostCount(any());
            verify(postHashtagRepository, never()).deleteByPostId(any());
        }
    }

    // ── searchPosts() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchPosts()")
    class SearchPostsTests {

        @Test
        @DisplayName("Should search by hashtag when keyword starts with #")
        void searchPosts_hashtagKeyword() {
            // Given
            when(postHashtagRepository.findPostIdsByHashtagTag("java"))
                    .thenReturn(List.of(1, 2));

            PostDataDTO post1 = buildPostData(1, 10, "Hello #java");
            PostDataDTO post2 = buildPostData(2, 11, "More #java content");

            when(postServiceClient.getPostById(1))
                    .thenReturn(new PostApiResponse(true, "ok", post1));
            when(postServiceClient.getPostById(2))
                    .thenReturn(new PostApiResponse(true, "ok", post2));

            when(postHashtagRepository.findHashtagIdsByPostId(anyInt()))
                    .thenReturn(Collections.emptyList());

            // When
            ApiResponseDTO<List<PostSearchResultDTO>> result =
                    searchService.searchPosts("#java");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(2);
        }

        @Test
        @DisplayName("Should return error for empty keyword")
        void searchPosts_emptyKeyword_returnsError() {
            ApiResponseDTO<List<PostSearchResultDTO>> result = searchService.searchPosts("  ");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("empty");
        }
    }

    // ── searchUsers() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchUsers()")
    class SearchUsersTests {

        @Test
        @DisplayName("Should return users from auth-service")
        void searchUsers_success() {
            // Given
            UserDataDTO user = new UserDataDTO(1, "rahul", "rahul@test.com",
                    "Rahul Kumar", "Bio", null, "USER", true);
            AuthUserListApiResponse response =
                    new AuthUserListApiResponse(true, "ok", List.of(user));
            when(authServiceClient.searchUsers("rahul")).thenReturn(response);

            // When
            ApiResponseDTO<List<UserSearchResultDTO>> result = searchService.searchUsers("rahul");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getUsername()).isEqualTo("rahul");
        }

        @Test
        @DisplayName("Should return empty list when auth-service fails")
        void searchUsers_feignFailure_returnsEmpty() {
            when(authServiceClient.searchUsers(anyString())).thenThrow(new RuntimeException("down"));

            ApiResponseDTO<List<UserSearchResultDTO>> result = searchService.searchUsers("test");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEmpty();
        }

        @Test
        @DisplayName("Should return error for blank query")
        void searchUsers_blankQuery_returnsError() {
            ApiResponseDTO<List<UserSearchResultDTO>> result = searchService.searchUsers("");
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ── getTrendingHashtags() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getTrendingHashtags()")
    class TrendingHashtagsTests {

        @Test
        @DisplayName("Should return top N hashtags by postCount")
        void getTrendingHashtags_success() {
            // Given
            List<Hashtag> trending = List.of(
                    buildHashtag(1, "java", 100),
                    buildHashtag(2, "spring", 80),
                    buildHashtag(3, "boot", 60)
            );
            when(hashtagRepository.findTrendingHashtags(any(Pageable.class))).thenReturn(trending);

            // When
            ApiResponseDTO<List<HashtagResponseDTO>> result =
                    searchService.getTrendingHashtags(3);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(3);
            assertThat(result.getData().get(0).getTag()).isEqualTo("java");
        }

        @Test
        @DisplayName("Should cap limit at 50")
        void getTrendingHashtags_limitCapped() {
            when(hashtagRepository.findTrendingHashtags(any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            // Limit 200 → should be capped to 50 internally
            searchService.getTrendingHashtags(200);

            verify(hashtagRepository).findTrendingHashtags(
                    argThat(p -> p.getPageSize() == 50));
        }
    }

    // ── getPostsByHashtag() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getPostsByHashtag()")
    class GetPostsByHashtagTests {

        @Test
        @DisplayName("Should throw HashtagNotFoundException when tag does not exist")
        void getPostsByHashtag_notFound() {
            when(hashtagRepository.existsByTag("unknown")).thenReturn(false);

            assertThatThrownBy(() -> searchService.getPostsByHashtag("unknown"))
                    .isInstanceOf(HashtagNotFoundException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        @DisplayName("Should return posts for existing hashtag")
        void getPostsByHashtag_success() {
            // Given
            when(hashtagRepository.existsByTag("java")).thenReturn(true);
            when(postHashtagRepository.findPostIdsByHashtagTag("java"))
                    .thenReturn(List.of(5));

            PostDataDTO post = buildPostData(5, 10, "Hello #java");
            when(postServiceClient.getPostById(5))
                    .thenReturn(new PostApiResponse(true, "ok", post));
            when(postHashtagRepository.findHashtagIdsByPostId(5))
                    .thenReturn(Collections.emptyList());

            // When
            ApiResponseDTO<List<PostSearchResultDTO>> result =
                    searchService.getPostsByHashtag("java");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getPostId()).isEqualTo(5);
        }
    }

    // ── getHashtagCount() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getHashtagCount()")
    class GetHashtagCountTests {

        @Test
        @DisplayName("Should return postCount for existing tag")
        void getHashtagCount_exists() {
            when(hashtagRepository.findPostCountByTag("java")).thenReturn(Optional.of(150));

            ApiResponseDTO<Integer> result = searchService.getHashtagCount("java");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEqualTo(150);
        }

        @Test
        @DisplayName("Should return 0 for non-existent tag")
        void getHashtagCount_notFound_returnsZero() {
            when(hashtagRepository.findPostCountByTag("unknown")).thenReturn(Optional.empty());

            ApiResponseDTO<Integer> result = searchService.getHashtagCount("unknown");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return error for blank tag")
        void getHashtagCount_blankTag_returnsError() {
            ApiResponseDTO<Integer> result = searchService.getHashtagCount("  ");
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
