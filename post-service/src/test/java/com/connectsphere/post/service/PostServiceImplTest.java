package com.connectsphere.post.service;

import com.connectsphere.post.dto.*;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import com.connectsphere.post.exception.PostNotFoundException;
import com.connectsphere.post.exception.UnauthorizedActionException;
import com.connectsphere.post.repository.PostRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostServiceImplTest - Unit tests for Post Service business logic
 *
 * Uses Mockito to mock PostRepository so no DB is needed.
 * Tests cover:
 *  - createPost (happy path, media post)
 *  - getPostById (found, not found)
 *  - getPostsByUser
 *  - getFeedForUser (with followees, empty)
 *  - updatePost (own post, another user's post)
 *  - deletePost (author delete, admin delete, unauthorized)
 *  - searchPosts
 *  - incrementLikes, decrementLikes
 *  - incrementComments, decrementComments
 *  - incrementShares
 *  - changeVisibility (own, unauthorized, invalid value)
 *  - getPostCount
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostServiceImpl Unit Tests")
class PostServiceImplTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private com.connectsphere.post.messaging.PostEventPublisher eventPublisher;

    @Mock
    private com.connectsphere.post.client.FollowServiceClient followServiceClient;

    @InjectMocks
    private PostServiceImpl postService;

    // ── Test Data ────

    private Post buildPost(Integer postId, Integer authorId, Visibility visibility) {
        return Post.builder()
                .postId(postId)
                .authorId(authorId)
                .content("Test post content #connectsphere")
                .mediaUrls(List.of())
                .postType(PostType.TEXT)
                .visibility(visibility)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── createPost ───────

    @Nested
    @DisplayName("createPost()")
    class CreatePostTests {

        @Test
        @DisplayName("Should create a TEXT post successfully")
        void createPost_textPost_success() {

            // Given
            Integer authorId = 1;
            CreatePostRequestDTO request = CreatePostRequestDTO.builder()
                    .content("Hello ConnectSphere!")
                    .visibility(Visibility.PUBLIC)
                    .postType(PostType.TEXT)
                    .build();

            Post savedPost = buildPost(10, authorId, Visibility.PUBLIC);
            savedPost.setContent("Hello ConnectSphere!");

            when(postRepository.save(any(Post.class))).thenReturn(savedPost);

            // When
            ApiResponseDTO<PostResponseDTO> response = postService.createPost(authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Post created successfully");
            assertThat(response.getData()).isNotNull();
            assertThat(response.getData().getAuthorId()).isEqualTo(authorId);
            assertThat(response.getData().getContent()).isEqualTo("Hello ConnectSphere!");
            assertThat(response.getData().getVisibility()).isEqualTo(Visibility.PUBLIC);
            assertThat(response.getData().getPostType()).isEqualTo(PostType.TEXT);
            assertThat(response.getData().getLikesCount()).isZero();
            assertThat(response.getData().getCommentsCount()).isZero();

            verify(postRepository, times(1)).save(any(Post.class));
        }

        @Test
        @DisplayName("Should create an IMAGE post with media URLs")
        void createPost_imagePost_success() {

            // Given
            Integer authorId = 2;
            CreatePostRequestDTO request = CreatePostRequestDTO.builder()
                    .content("Check out this photo!")
                    .mediaUrls(List.of("https://cdn.connectsphere.com/img/photo1.jpg"))
                    .postType(PostType.IMAGE)
                    .visibility(Visibility.FOLLOWERS_ONLY)
                    .build();

            Post savedPost = Post.builder()
                    .postId(11)
                    .authorId(authorId)
                    .content("Check out this photo!")
                    .mediaUrls(List.of("https://cdn.connectsphere.com/img/photo1.jpg"))
                    .postType(PostType.IMAGE)
                    .visibility(Visibility.FOLLOWERS_ONLY)
                    .likesCount(0).commentsCount(0).sharesCount(0)
                    .isDeleted(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(postRepository.save(any(Post.class))).thenReturn(savedPost);

            // When
            ApiResponseDTO<PostResponseDTO> response = postService.createPost(authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getPostType()).isEqualTo(PostType.IMAGE);
            assertThat(response.getData().getVisibility()).isEqualTo(Visibility.FOLLOWERS_ONLY);
            assertThat(response.getData().getMediaUrls()).hasSize(1);
            verify(postRepository).save(any(Post.class));
        }

        @Test
        @DisplayName("Should default visibility to PUBLIC when not provided")
        void createPost_defaultsToPublicVisibility() {

            // Given
            Integer authorId = 3;
            CreatePostRequestDTO request = CreatePostRequestDTO.builder()
                    .content("Default visibility post")
                    .build();  // visibility not set

            Post savedPost = buildPost(12, authorId, Visibility.PUBLIC);
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);

            // When
            ApiResponseDTO<PostResponseDTO> response = postService.createPost(authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getVisibility()).isEqualTo(Visibility.PUBLIC);
        }

        @Test
        @DisplayName("Should create PRIVATE post successfully")
        void createPost_privatePost_success() {

            // Given
            Integer authorId = 4;
            CreatePostRequestDTO request = CreatePostRequestDTO.builder()
                    .content("My private thoughts")
                    .visibility(Visibility.PRIVATE)
                    .postType(PostType.TEXT)
                    .build();

            Post savedPost = buildPost(13, authorId, Visibility.PRIVATE);
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);

            // When
            ApiResponseDTO<PostResponseDTO> response = postService.createPost(authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getVisibility()).isEqualTo(Visibility.PRIVATE);
        }
    }

    // ── getPostById ───

    @Nested
    @DisplayName("getPostById()")
    class GetPostByIdTests {

        @Test
        @DisplayName("Should return post when found and not deleted")
        void getPostById_found_success() {

            // Given
            Post post = buildPost(1, 10, Visibility.PUBLIC);
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            // When
            ApiResponseDTO<PostResponseDTO> response = postService.getPostById(1);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getPostId()).isEqualTo(1);
            assertThat(response.getData().getAuthorId()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should throw PostNotFoundException when post not found")
        void getPostById_notFound_throwsException() {

            // Given
            when(postRepository.findByPostIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> postService.getPostById(999))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessageContaining("999");

            verify(postRepository).findByPostIdAndIsDeletedFalse(999);
        }

        @Test
        @DisplayName("Should throw PostNotFoundException for soft-deleted post")
        void getPostById_deletedPost_throwsException() {

            // Given — soft-deleted posts are excluded by the repo query
            when(postRepository.findByPostIdAndIsDeletedFalse(5))
                    .thenReturn(Optional.empty());  // repo returns empty for deleted

            // When / Then
            assertThatThrownBy(() -> postService.getPostById(5))
                    .isInstanceOf(PostNotFoundException.class);
        }
    }

    // ── getPostsByUser ────

    @Nested
    @DisplayName("getPostsByUser()")
    class GetPostsByUserTests {

        @Test
        @DisplayName("Should return all non-deleted posts for a user")
        void getPostsByUser_returnsList() {

            // Given
            Integer authorId = 5;
            List<Post> posts = List.of(
                    buildPost(1, authorId, Visibility.PUBLIC),
                    buildPost(2, authorId, Visibility.FOLLOWERS_ONLY),
                    buildPost(3, authorId, Visibility.PUBLIC)
            );
            when(postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId))
                    .thenReturn(posts);

            // When
            ApiResponseDTO<List<PostResponseDTO>> response = postService.getPostsByUser(authorId, null, null);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(3);
            assertThat(response.getData())
                    .allMatch(dto -> dto.getAuthorId().equals(authorId));
        }

        @Test
        @DisplayName("Should return empty list when user has no posts")
        void getPostsByUser_noPostsFound_emptyList() {

            // Given
            when(postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(99))
                    .thenReturn(List.of());

            // When
            ApiResponseDTO<List<PostResponseDTO>> response = postService.getPostsByUser(99, null, null);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── getFeedForUser ───

    @Nested
    @DisplayName("getFeedForUser()")
    class GetFeedForUserTests {

        @Test
        @DisplayName("Should return personalised feed from followees")
        void getFeedForUser_withFollowees_returnsFeed() {

            // Given
            Integer userId = 1;
            String authHeader = "Bearer token";
            List<Integer> followeeIds = List.of(1); // Service adds self
            // Note: In real logic, service calls followServiceClient to get more IDs
            
            List<Post> feedPosts = List.of(
                    buildPost(10, 1, Visibility.PUBLIC),
                    buildPost(11, 1, Visibility.FOLLOWERS_ONLY)
            );
            
            // We need to mock the followServiceClient as well since the service now calls it
            when(followServiceClient.getFolloweeIds(eq(userId), eq(authHeader)))
                .thenReturn(ApiResponseDTO.success("Success", List.of()));

            when(postRepository.findFeedPersonalized(anyList(), eq(userId)))
                .thenReturn(feedPosts);

            // When
            ApiResponseDTO<List<PostResponseDTO>> response = postService.getFeedForUser(userId, authHeader);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            verify(postRepository).findFeedPersonalized(anyList(), eq(userId));
        }

        @Test
        @DisplayName("Should return only self posts when following list is empty")
        void getFeedForUser_emptyFolloweeList_selfFeed() {
            // Given
            Integer userId = 1;
            String authHeader = "Bearer token";
            
            // Mock follow service returning empty list
            when(followServiceClient.getFolloweeIds(eq(userId), eq(authHeader)))
                .thenReturn(ApiResponseDTO.success("Success", List.of()));

            // Mock repo returning only self posts
            when(postRepository.findFeedPersonalized(anyList(), eq(userId)))
                .thenReturn(List.of(buildPost(100, 1, Visibility.PUBLIC)));

            // When
            ApiResponseDTO<List<PostResponseDTO>> response = postService.getFeedForUser(userId, authHeader);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(1);
            verify(postRepository).findFeedPersonalized(anyList(), eq(userId));
        }

        @Test
        @DisplayName("Should return empty feed when userId is null")
        void getFeedForUser_nullUserId_emptyFeed() {

            // When
            ApiResponseDTO<List<PostResponseDTO>> response = postService.getFeedForUser(null, null);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
            verify(postRepository, never()).findFeedPersonalized(anyList(), any());
        }
    }

    // ── updatePost ────

    @Nested
    @DisplayName("updatePost()")
    class UpdatePostTests {

        @Test
        @DisplayName("Should update post content when requester is the author")
        void updatePost_ownPost_success() {

            // Given
            Integer postId = 1;
            Integer authorId = 10;
            Post existingPost = buildPost(postId, authorId, Visibility.PUBLIC);

            UpdatePostRequestDTO request = UpdatePostRequestDTO.builder()
                    .content("Updated content")
                    .build();

            Post updatedPost = buildPost(postId, authorId, Visibility.PUBLIC);
            updatedPost.setContent("Updated content");

            when(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .thenReturn(Optional.of(existingPost));
            when(postRepository.save(any(Post.class))).thenReturn(updatedPost);

            // When
            ApiResponseDTO<PostResponseDTO> response =
                    postService.updatePost(postId, authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Post updated successfully");
            assertThat(response.getData().getContent()).isEqualTo("Updated content");
            verify(postRepository).save(any(Post.class));
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-author tries to update")
        void updatePost_notOwner_throwsException() {

            // Given
            Post post = buildPost(1, 10, Visibility.PUBLIC);  // owned by userId=10
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            UpdatePostRequestDTO request = UpdatePostRequestDTO.builder()
                    .content("Hacked content")
                    .build();

            // When / Then — userId=99 tries to update userId=10's post
            assertThatThrownBy(() -> postService.updatePost(1, 99, request))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("not authorized");

            verify(postRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PostNotFoundException when post does not exist")
        void updatePost_postNotFound_throwsException() {

            // Given
            when(postRepository.findByPostIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> postService.updatePost(
                    999, 1, UpdatePostRequestDTO.builder().content("x").build()))
                    .isInstanceOf(PostNotFoundException.class);
        }
    }

    // ── deletePost ───

    @Nested
    @DisplayName("deletePost()")
    class DeletePostTests {

        @Test
        @DisplayName("Should soft-delete post when requester is the author")
        void deletePost_ownPost_success() {

            // Given
            Post post = buildPost(1, 10, Visibility.PUBLIC);
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            // When
            ApiResponseDTO<String> response = postService.deletePost(1, 10, "USER");

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Post deleted successfully");
            verify(postRepository).softDeleteByPostId(1);
        }

        @Test
        @DisplayName("Should allow ADMIN to delete any post")
        void deletePost_adminDeletesAnyPost_success() {

            // Given — post owned by userId=10, admin is userId=99
            Post post = buildPost(1, 10, Visibility.PUBLIC);
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            // When
            ApiResponseDTO<String> response = postService.deletePost(1, 99, "ADMIN");

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(postRepository).softDeleteByPostId(1);
        }

        @Test
        @DisplayName("Should allow MODERATOR to delete any post")
        void deletePost_moderatorDeletesAnyPost_success() {

            // Given
            Post post = buildPost(1, 10, Visibility.PUBLIC);
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            // When
            ApiResponseDTO<String> response = postService.deletePost(1, 88, "MODERATOR");

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(postRepository).softDeleteByPostId(1);
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-author USER tries to delete")
        void deletePost_notOwner_throwsException() {

            // Given
            Post post = buildPost(1, 10, Visibility.PUBLIC);  // owned by userId=10
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            // When / Then — userId=55 is not owner and not admin
            assertThatThrownBy(() -> postService.deletePost(1, 55, "USER"))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("not authorized");

            verify(postRepository, never()).softDeleteByPostId(anyInt());
        }

        @Test
        @DisplayName("Should throw PostNotFoundException when post not found")
        void deletePost_postNotFound_throwsException() {

            // Given
            when(postRepository.findByPostIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> postService.deletePost(999, 1, "USER"))
                    .isInstanceOf(PostNotFoundException.class);
        }
    }

    // ── searchPosts ───
    @Nested
    @DisplayName("searchPosts()")
    class SearchPostsTests {

        @Test
        @DisplayName("Should return matching posts for keyword")
        void searchPosts_matchFound_returnsList() {

            // Given
            List<Post> results = List.of(
                    buildPost(1, 1, Visibility.PUBLIC),
                    buildPost(2, 2, Visibility.PUBLIC)
            );
            when(postRepository.searchByContent("connectsphere")).thenReturn(results);

            // When
            ApiResponseDTO<List<PostResponseDTO>> response = postService.searchPosts("connectsphere");

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            verify(postRepository).searchByContent("connectsphere");
        }

        @Test
        @DisplayName("Should return empty list when no posts match keyword")
        void searchPosts_noMatch_emptyList() {

            // Given
            when(postRepository.searchByContent("xyznotfound")).thenReturn(List.of());

            // When
            ApiResponseDTO<List<PostResponseDTO>> response = postService.searchPosts("xyznotfound");

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── Counter operations ───

    @Nested
    @DisplayName("Counter Operations (incrementLikes, decrementLikes, etc.)")
    class CounterOperationTests {

        @Test
        @DisplayName("Should increment likes count successfully")
        void incrementLikes_existingPost_success() {

            // Given
            Integer postId = 1;
            when(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .thenReturn(Optional.of(buildPost(postId, 1, Visibility.PUBLIC)));

            // When
            ApiResponseDTO<String> response = postService.incrementLikes(postId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(postRepository).incrementLikes(postId);
        }

        @Test
        @DisplayName("Should throw PostNotFoundException on incrementLikes for missing post")
        void incrementLikes_postNotFound_throwsException() {

            // Given
            when(postRepository.findByPostIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> postService.incrementLikes(999))
                    .isInstanceOf(PostNotFoundException.class);

            verify(postRepository, never()).incrementLikes(anyInt());
        }

        @Test
        @DisplayName("Should decrement likes count successfully")
        void decrementLikes_existingPost_success() {

            // Given
            Integer postId = 2;
            when(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .thenReturn(Optional.of(buildPost(postId, 1, Visibility.PUBLIC)));

            // When
            ApiResponseDTO<String> response = postService.decrementLikes(postId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(postRepository).decrementLikes(postId);
        }

        @Test
        @DisplayName("Should increment comments count successfully")
        void incrementComments_existingPost_success() {

            // Given
            Integer postId = 3;
            when(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .thenReturn(Optional.of(buildPost(postId, 1, Visibility.PUBLIC)));

            // When
            ApiResponseDTO<String> response = postService.incrementComments(postId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(postRepository).incrementComments(postId);
        }

        @Test
        @DisplayName("Should decrement comments count successfully")
        void decrementComments_existingPost_success() {

            // Given
            Integer postId = 4;
            when(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .thenReturn(Optional.of(buildPost(postId, 1, Visibility.PUBLIC)));

            // When
            ApiResponseDTO<String> response = postService.decrementComments(postId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(postRepository).decrementComments(postId);
        }

        @Test
        @DisplayName("Should increment shares count successfully")
        void incrementShares_existingPost_success() {

            // Given
            Integer postId = 5;
            when(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .thenReturn(Optional.of(buildPost(postId, 1, Visibility.PUBLIC)));

            // When
            ApiResponseDTO<String> response = postService.incrementShares(postId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(postRepository).incrementShares(postId);
        }
    }

    // ── changeVisibility ────

    @Nested
    @DisplayName("changeVisibility()")
    class ChangeVisibilityTests {

        @Test
        @DisplayName("Should change visibility from PUBLIC to PRIVATE for post author")
        void changeVisibility_ownPost_success() {

            // Given
            Post post = buildPost(1, 10, Visibility.PUBLIC);
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            // When
            ApiResponseDTO<PostResponseDTO> response =
                    postService.changeVisibility(1, 10, "PRIVATE");

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Visibility updated successfully");
            verify(postRepository).updateVisibility(1, Visibility.PRIVATE);
        }

        @Test
        @DisplayName("Should change visibility to FOLLOWERS_ONLY successfully")
        void changeVisibility_toFollowersOnly_success() {

            // Given
            Post post = buildPost(2, 20, Visibility.PUBLIC);
            when(postRepository.findByPostIdAndIsDeletedFalse(2))
                    .thenReturn(Optional.of(post));

            // When
            ApiResponseDTO<PostResponseDTO> response =
                    postService.changeVisibility(2, 20, "FOLLOWERS_ONLY");

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(postRepository).updateVisibility(2, Visibility.FOLLOWERS_ONLY);
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-author changes visibility")
        void changeVisibility_notOwner_throwsException() {

            // Given
            Post post = buildPost(1, 10, Visibility.PUBLIC);
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            // When / Then
            assertThatThrownBy(() -> postService.changeVisibility(1, 99, "PRIVATE"))
                    .isInstanceOf(UnauthorizedActionException.class);

            verify(postRepository, never()).updateVisibility(anyInt(), any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid visibility value")
        void changeVisibility_invalidValue_throwsException() {

            // Given
            Post post = buildPost(1, 10, Visibility.PUBLIC);
            when(postRepository.findByPostIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(post));

            // When / Then
            assertThatThrownBy(() -> postService.changeVisibility(1, 10, "EVERYONE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid visibility value");
        }
    }

    // ── getPostCount ─────
    @Nested
    @DisplayName("getPostCount()")
    class GetPostCountTests {

        @Test
        @DisplayName("Should return correct post count for a user")
        void getPostCount_returnsCount() {

            // Given
            when(postRepository.countByAuthorIdAndIsDeletedFalse(10)).thenReturn(5);

            // When
            ApiResponseDTO<Integer> response = postService.getPostCount(10);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(5);
            verify(postRepository).countByAuthorIdAndIsDeletedFalse(10);
        }

        @Test
        @DisplayName("Should return zero when user has no posts")
        void getPostCount_noPosts_returnsZero() {

            // Given
            when(postRepository.countByAuthorIdAndIsDeletedFalse(99)).thenReturn(0);

            // When
            ApiResponseDTO<Integer> response = postService.getPostCount(99);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isZero();
        }
    }
}