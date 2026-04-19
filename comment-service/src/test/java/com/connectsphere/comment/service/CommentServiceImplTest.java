package com.connectsphere.comment.service;

import com.connectsphere.comment.client.PostClient;
import com.connectsphere.comment.dto.*;
import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.exception.CommentNotFoundException;
import com.connectsphere.comment.exception.UnauthorizedActionException;
import com.connectsphere.comment.repository.CommentRepository;
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
 * CommentServiceImplTest — Fixed Unit Tests for Comment Service
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentServiceImpl Unit Tests")
class CommentServiceImplTest {

    // FIX 1 — PostClient instead of RestTemplate
    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostClient postClient;

    @InjectMocks
    private CommentServiceImpl commentService;

    // FIX 2 — @BeforeEach removed entirely (postServiceBaseUrl field is gone)

    // ── Test Data Helper ──────────────────────────────────────────────────────

    private Comment buildComment(Integer commentId, Integer postId, Integer authorId,
                                 Integer parentCommentId, boolean isDeleted) {
        return Comment.builder()
                .commentId(commentId)
                .postId(postId)
                .authorId(authorId)
                .parentCommentId(parentCommentId)
                .content("Test comment content")
                .likesCount(0)
                .isDeleted(isDeleted)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * FIX 3 helper — builds a valid PostApiResponse so verifyPostExists() passes.
     * Call this at the start of every addComment test.
     */
    private PostApiResponse buildValidPostResponse(Integer postId) {
        PostResponseDTO post = new PostResponseDTO();
        post.setPostId(postId);
        post.setIsDeleted(false);

        PostApiResponse response = new PostApiResponse();
        response.setSuccess(true);
        response.setData(post);
        return response;
    }

    // ── addComment ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addComment()")
    class AddCommentTests {

        @Test
        @DisplayName("Should add a top-level comment successfully")
        void addComment_topLevel_success() {

            // Given
            Integer authorId = 1;
            Integer postId   = 10;

            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(postId)
                    .parentCommentId(null)
                    .content("Great post!")
                    .build();

            // FIX 3 — mock verifyPostExists() so it passes
            when(postClient.getPostById(postId)).thenReturn(buildValidPostResponse(postId));

            Comment saved = buildComment(100, postId, authorId, null, false);
            saved.setContent("Great post!");
            when(commentRepository.save(any(Comment.class))).thenReturn(saved);

            // When
            ApiResponseDTO<CommentResponseDTO> response =
                    commentService.addComment(authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment added successfully");
            assertThat(response.getData().getPostId()).isEqualTo(postId);
            assertThat(response.getData().getAuthorId()).isEqualTo(authorId);
            assertThat(response.getData().getParentCommentId()).isNull();
            assertThat(response.getData().getContent()).isEqualTo("Great post!");
            assertThat(response.getData().getIsDeleted()).isFalse();

            verify(postClient).getPostById(postId);
            verify(commentRepository).save(any(Comment.class));
            // FIX 4 — verify postClient increment, not restTemplate
            verify(postClient).incrementCommentCount(postId);
        }

        @Test
        @DisplayName("Should add a reply to an existing comment successfully")
        void addComment_reply_success() {

            // Given
            Integer authorId        = 2;
            Integer postId          = 10;
            Integer parentCommentId = 50;

            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(postId)
                    .parentCommentId(parentCommentId)
                    .content("Nice point!")
                    .build();

            // FIX 3 — mock verifyPostExists()
            when(postClient.getPostById(postId)).thenReturn(buildValidPostResponse(postId));

            // Parent comment exists and is active
            Comment parentComment = buildComment(parentCommentId, postId, 1, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(parentCommentId))
                    .thenReturn(Optional.of(parentComment));

            Comment savedReply = buildComment(101, postId, authorId, parentCommentId, false);
            savedReply.setContent("Nice point!");
            when(commentRepository.save(any(Comment.class))).thenReturn(savedReply);

            // When
            ApiResponseDTO<CommentResponseDTO> response =
                    commentService.addComment(authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getParentCommentId()).isEqualTo(parentCommentId);
            assertThat(response.getData().getContent()).isEqualTo("Nice point!");

            verify(postClient).getPostById(postId);
            verify(commentRepository).findByCommentIdAndIsDeletedFalse(parentCommentId);
            verify(commentRepository).save(any(Comment.class));
            verify(postClient).incrementCommentCount(postId);
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException when replying to a deleted parent")
        void addComment_replyToDeletedParent_throwsException() {

            // Given
            Integer postId = 10;

            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(postId)
                    .parentCommentId(999)
                    .content("Reply to deleted comment")
                    .build();

            // FIX 3 — post must be valid first; then parent comment lookup fails
            when(postClient.getPostById(postId)).thenReturn(buildValidPostResponse(postId));
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> commentService.addComment(1, request))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessageContaining("999");

            verify(commentRepository, never()).save(any());
            verify(postClient, never()).incrementCommentCount(anyInt());
        }

        @Test
        @DisplayName("Should still save comment when post-service increment call fails (graceful degradation)")
        void addComment_postServiceIncrementFails_commentStillSaved() {

            // Given
            Integer postId   = 10;
            Integer authorId = 1;

            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(postId)
                    .parentCommentId(null)
                    .content("Comment despite service failure")
                    .build();

            // FIX 3 — post must exist first (verifyPostExists passes)
            when(postClient.getPostById(postId)).thenReturn(buildValidPostResponse(postId));

            Comment saved = buildComment(102, postId, authorId, null, false);
            when(commentRepository.save(any(Comment.class))).thenReturn(saved);

            // FIX 5 — simulate the INCREMENT call failing, not the verify call
            doThrow(new RuntimeException("Connection refused"))
                    .when(postClient).incrementCommentCount(postId);

            // When — should NOT throw; CommentServiceImpl catches this gracefully
            ApiResponseDTO<CommentResponseDTO> response =
                    commentService.addComment(authorId, request);

            // Then — comment IS saved despite the increment failure
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment added successfully");
            verify(commentRepository).save(any(Comment.class));
            verify(postClient).incrementCommentCount(postId);
        }

        @Test
        @DisplayName("Should throw exception when post does not exist in post-service")
        void addComment_postNotFound_throwsException() {

            // Given — post-service returns null (post doesn't exist)
            Integer postId = 999;

            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(postId)
                    .parentCommentId(null)
                    .content("Comment on non-existent post")
                    .build();

            when(postClient.getPostById(postId)).thenReturn(null);

            // When / Then
            assertThatThrownBy(() -> commentService.addComment(1, request))
                    .isInstanceOf(Exception.class);

            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when post is soft-deleted")
        void addComment_postSoftDeleted_throwsException() {

            // Given — post exists but isDeleted = true
            Integer postId = 10;

            PostResponseDTO deletedPost = new PostResponseDTO();
            deletedPost.setPostId(postId);
            deletedPost.setIsDeleted(true);   // ← soft-deleted

            PostApiResponse response = new PostApiResponse();
            response.setSuccess(true);
            response.setData(deletedPost);

            when(postClient.getPostById(postId)).thenReturn(response);

            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(postId)
                    .parentCommentId(null)
                    .content("Comment on deleted post")
                    .build();

            // When / Then
            assertThatThrownBy(() -> commentService.addComment(1, request))
                    .isInstanceOf(Exception.class);

            verify(commentRepository, never()).save(any());
        }
    }

    // ── getCommentsByPost ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCommentsByPost()")
    class GetCommentsByPostTests {

        @Test
        @DisplayName("Should return all comments including deleted ones with placeholder content")
        void getCommentsByPost_includesDeletedWithPlaceholder() {

            // Given
            Integer postId  = 10;
            Comment active  = buildComment(1, postId, 1, null, false);
            Comment deleted = buildComment(2, postId, 2, null, true);
            deleted.setContent("Original deleted content");

            when(commentRepository.findByPostIdOrderByCreatedAtAsc(postId))
                    .thenReturn(List.of(active, deleted));

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByPost(postId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);

            // Active comment retains original content
            assertThat(response.getData().get(0).getContent())
                    .isEqualTo("Test comment content");
            assertThat(response.getData().get(0).getIsDeleted()).isFalse();

            // Deleted comment gets placeholder text — original is hidden
            assertThat(response.getData().get(1).getContent())
                    .isEqualTo("[This comment was deleted]");
            assertThat(response.getData().get(1).getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("Should return empty list when post has no comments")
        void getCommentsByPost_noComments_returnsEmptyList() {

            // Given
            when(commentRepository.findByPostIdOrderByCreatedAtAsc(99))
                    .thenReturn(List.of());

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByPost(99);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("Should return all comments ordered by createdAt ascending")
        void getCommentsByPost_orderedByCreatedAt() {

            // Given
            Integer postId = 10;
            Comment first  = buildComment(1, postId, 1, null, false);
            Comment second = buildComment(2, postId, 2, null, false);
            Comment third  = buildComment(3, postId, 3, null, false);

            when(commentRepository.findByPostIdOrderByCreatedAtAsc(postId))
                    .thenReturn(List.of(first, second, third));

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByPost(postId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(3);
            // IDs in same order as returned by repo (oldest first)
            assertThat(response.getData().get(0).getCommentId()).isEqualTo(1);
            assertThat(response.getData().get(1).getCommentId()).isEqualTo(2);
            assertThat(response.getData().get(2).getCommentId()).isEqualTo(3);
        }
    }

    // ── getTopLevelComments ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getTopLevelComments()")
    class GetTopLevelCommentsTests {

        @Test
        @DisplayName("Should return only top-level comments (parentCommentId is null)")
        void getTopLevelComments_returnsOnlyTopLevel() {

            // Given
            Integer postId = 10;
            Comment c1 = buildComment(1, postId, 1, null, false);
            Comment c2 = buildComment(2, postId, 2, null, false);

            when(commentRepository.findTopLevelByPostId(postId))
                    .thenReturn(List.of(c1, c2));

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getTopLevelComments(postId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData())
                    .allMatch(dto -> dto.getParentCommentId() == null);
        }

        @Test
        @DisplayName("Should return empty list when post has no top-level comments")
        void getTopLevelComments_noComments_returnsEmptyList() {

            // Given
            when(commentRepository.findTopLevelByPostId(99)).thenReturn(List.of());

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getTopLevelComments(99);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── getCommentById ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCommentById()")
    class GetCommentByIdTests {

        @Test
        @DisplayName("Should return comment successfully when found")
        void getCommentById_found_success() {

            // Given
            Comment comment = buildComment(1, 10, 1, null, false);
            when(commentRepository.findByCommentId(1)).thenReturn(Optional.of(comment));

            // When
            ApiResponseDTO<CommentResponseDTO> response = commentService.getCommentById(1);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getCommentId()).isEqualTo(1);
            assertThat(response.getData().getPostId()).isEqualTo(10);
            assertThat(response.getData().getAuthorId()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException when comment does not exist")
        void getCommentById_notFound_throwsException() {

            // Given
            when(commentRepository.findByCommentId(999)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> commentService.getCommentById(999))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("Should return deleted comment with placeholder content (thread preserved)")
        void getCommentById_deletedComment_returnsPlaceholder() {

            // Given
            Comment deleted = buildComment(5, 10, 1, null, true);
            when(commentRepository.findByCommentId(5)).thenReturn(Optional.of(deleted));

            // When
            ApiResponseDTO<CommentResponseDTO> response = commentService.getCommentById(5);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getContent())
                    .isEqualTo("[This comment was deleted]");
            assertThat(response.getData().getIsDeleted()).isTrue();
            // CommentId still returned so thread structure is intact
            assertThat(response.getData().getCommentId()).isEqualTo(5);
        }
    }

    // ── getReplies ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getReplies()")
    class GetRepliesTests {

        @Test
        @DisplayName("Should return all replies for a parent comment")
        void getReplies_returnsReplies() {

            // Given
            Integer parentId = 10;
            List<Comment> replies = List.of(
                    buildComment(20, 1, 2, parentId, false),
                    buildComment(21, 1, 3, parentId, false)
            );
            when(commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentId))
                    .thenReturn(replies);

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getReplies(parentId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData())
                    .allMatch(dto -> dto.getParentCommentId().equals(parentId));
        }

        @Test
        @DisplayName("Should return empty list when comment has no replies")
        void getReplies_noReplies_returnsEmptyList() {

            // Given
            when(commentRepository.findByParentCommentIdOrderByCreatedAtAsc(99))
                    .thenReturn(List.of());

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getReplies(99);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("Should include deleted replies with placeholder content")
        void getReplies_includesDeletedReplies_withPlaceholder() {

            // Given
            Integer parentId     = 10;
            Comment activeReply  = buildComment(20, 1, 2, parentId, false);
            Comment deletedReply = buildComment(21, 1, 3, parentId, true);

            when(commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentId))
                    .thenReturn(List.of(activeReply, deletedReply));

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getReplies(parentId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData().get(1).getContent())
                    .isEqualTo("[This comment was deleted]");
        }
    }

    // ── updateComment ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateComment()")
    class UpdateCommentTests {

        @Test
        @DisplayName("Should update comment content when requester is the author")
        void updateComment_ownComment_success() {

            // Given
            Integer commentId = 1;
            Integer authorId  = 10;
            Comment existing  = buildComment(commentId, 5, authorId, null, false);

            UpdateCommentRequestDTO request = UpdateCommentRequestDTO.builder()
                    .content("Updated content")
                    .build();

            Comment updated = buildComment(commentId, 5, authorId, null, false);
            updated.setContent("Updated content");

            when(commentRepository.findByCommentIdAndIsDeletedFalse(commentId))
                    .thenReturn(Optional.of(existing));
            when(commentRepository.save(any(Comment.class))).thenReturn(updated);

            // When
            ApiResponseDTO<CommentResponseDTO> response =
                    commentService.updateComment(commentId, authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment updated successfully");
            assertThat(response.getData().getContent()).isEqualTo("Updated content");
            verify(commentRepository).save(any(Comment.class));
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-author tries to update")
        void updateComment_notOwner_throwsUnauthorized() {

            // Given — comment owned by userId=10, attacker is userId=99
            Comment comment = buildComment(1, 5, 10, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));

            // When / Then
            assertThatThrownBy(() -> commentService.updateComment(
                    1, 99, UpdateCommentRequestDTO.builder().content("hack").build()))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("not authorized");

            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException when comment not found on update")
        void updateComment_commentNotFound_throwsException() {

            // Given
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> commentService.updateComment(
                    999, 1, UpdateCommentRequestDTO.builder().content("anything").build()))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ── deleteComment ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteComment()")
    class DeleteCommentTests {

        @Test
        @DisplayName("Should soft-delete comment when requester is the author")
        void deleteComment_ownComment_success() {

            // Given — comment is on postId=10, owned by userId=5
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));

            // When
            ApiResponseDTO<String> response = commentService.deleteComment(1, 5, "USER");

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment deleted successfully");
            // FIX 4 — verify softDelete and postClient decrement
            verify(commentRepository).softDeleteByCommentId(1);
            verify(postClient).decrementCommentCount(10);
        }

        @Test
        @DisplayName("Should allow ADMIN to delete any comment regardless of ownership")
        void deleteComment_adminDeletesAnyComment_success() {

            // Given — comment owned by userId=5, admin is userId=99
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));

            // When — admin bypasses ownership check
            ApiResponseDTO<String> response = commentService.deleteComment(1, 99, "ADMIN");

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment deleted successfully");
            verify(commentRepository).softDeleteByCommentId(1);
            verify(postClient).decrementCommentCount(10);
        }

        @Test
        @DisplayName("Should allow MODERATOR to delete any comment regardless of ownership")
        void deleteComment_moderatorDeletesAnyComment_success() {

            // Given — comment owned by userId=5, moderator is userId=88
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));

            // When
            ApiResponseDTO<String> response = commentService.deleteComment(1, 88, "MODERATOR");

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(commentRepository).softDeleteByCommentId(1);
            verify(postClient).decrementCommentCount(10);
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-author USER tries to delete")
        void deleteComment_notOwnerUser_throwsUnauthorized() {

            // Given — comment owned by userId=5, attacker is userId=77
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));

            // When / Then
            assertThatThrownBy(() -> commentService.deleteComment(1, 77, "USER"))
                    .isInstanceOf(UnauthorizedActionException.class);

            verify(commentRepository, never()).softDeleteByCommentId(anyInt());
            verify(postClient, never()).decrementCommentCount(anyInt());
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException when comment not found on delete")
        void deleteComment_commentNotFound_throwsException() {

            // Given
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> commentService.deleteComment(999, 1, "USER"))
                    .isInstanceOf(CommentNotFoundException.class);

            verify(commentRepository, never()).softDeleteByCommentId(anyInt());
        }

        @Test
        @DisplayName("Should still soft-delete when post-service decrement call fails (graceful degradation)")
        void deleteComment_postServiceDecrementFails_commentStillDeleted() {

            // Given
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));

            // Simulate decrement call failing
            doThrow(new RuntimeException("Connection refused"))
                    .when(postClient).decrementCommentCount(10);

            // When — should NOT throw; graceful degradation
            ApiResponseDTO<String> response = commentService.deleteComment(1, 5, "USER");

            // Then — comment IS deleted despite decrement failure
            assertThat(response.isSuccess()).isTrue();
            verify(commentRepository).softDeleteByCommentId(1);
        }
    }

    // ── likeComment / unlikeComment ───────────────────────────────────────────

    @Nested
    @DisplayName("likeComment() and unlikeComment()")
    class LikeUnlikeCommentTests {

        @Test
        @DisplayName("Should like a comment successfully")
        void likeComment_existingComment_success() {

            // Given
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(buildComment(1, 10, 1, null, false)));

            // When
            ApiResponseDTO<String> response = commentService.likeComment(1);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment liked successfully");
            verify(commentRepository).incrementLikes(1);
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException on likeComment when comment not found")
        void likeComment_commentNotFound_throwsException() {

            // Given
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> commentService.likeComment(999))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessageContaining("999");

            verify(commentRepository, never()).incrementLikes(anyInt());
        }

        @Test
        @DisplayName("Should unlike a comment successfully")
        void unlikeComment_existingComment_success() {

            // Given
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(buildComment(1, 10, 1, null, false)));

            // When
            ApiResponseDTO<String> response = commentService.unlikeComment(1);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment unliked successfully");
            verify(commentRepository).decrementLikes(1);
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException on unlikeComment when comment not found")
        void unlikeComment_commentNotFound_throwsException() {

            // Given
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> commentService.unlikeComment(999))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessageContaining("999");

            verify(commentRepository, never()).decrementLikes(anyInt());
        }

        @Test
        @DisplayName("Should not call incrementLikes on a deleted comment")
        void likeComment_deletedComment_throwsException() {

            // Given — findByCommentIdAndIsDeletedFalse returns empty for deleted comments
            when(commentRepository.findByCommentIdAndIsDeletedFalse(5))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> commentService.likeComment(5))
                    .isInstanceOf(CommentNotFoundException.class);

            verify(commentRepository, never()).incrementLikes(anyInt());
        }
    }

    // ── getCommentCount ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCommentCount()")
    class GetCommentCountTests {

        @Test
        @DisplayName("Should return correct active comment count for a post")
        void getCommentCount_returnsCorrectCount() {

            // Given
            when(commentRepository.countByPostIdAndIsDeletedFalse(10)).thenReturn(7);

            // When
            ApiResponseDTO<Integer> response = commentService.getCommentCount(10);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(7);
            verify(commentRepository).countByPostIdAndIsDeletedFalse(10);
        }

        @Test
        @DisplayName("Should return zero when post has no active comments")
        void getCommentCount_noComments_returnsZero() {

            // Given
            when(commentRepository.countByPostIdAndIsDeletedFalse(99)).thenReturn(0);

            // When
            ApiResponseDTO<Integer> response = commentService.getCommentCount(99);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isZero();
        }

        @Test
        @DisplayName("Should count only non-deleted comments (soft-deleted excluded)")
        void getCommentCount_onlyCountsActiveComments() {

            // Given — post has 10 total comments but only 6 are active
            when(commentRepository.countByPostIdAndIsDeletedFalse(10)).thenReturn(6);

            // When
            ApiResponseDTO<Integer> response = commentService.getCommentCount(10);

            // Then
            assertThat(response.getData()).isEqualTo(6);
            // Verifies the correct method (isDeletedFalse variant) was called
            verify(commentRepository).countByPostIdAndIsDeletedFalse(10);
        }
    }

    // ── getCommentsByUser ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCommentsByUser()")
    class GetCommentsByUserTests {

        @Test
        @DisplayName("Should return all active comments by a user")
        void getCommentsByUser_returnsUserComments() {

            // Given
            Integer authorId = 5;
            List<Comment> comments = List.of(
                    buildComment(1, 10, authorId, null, false),
                    buildComment(2, 20, authorId, 1,    false)
            );
            when(commentRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId))
                    .thenReturn(comments);

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByUser(authorId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData())
                    .allMatch(dto -> dto.getAuthorId().equals(authorId));
        }

        @Test
        @DisplayName("Should return empty list when user has made no comments")
        void getCommentsByUser_noComments_returnsEmptyList() {

            // Given
            when(commentRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(99))
                    .thenReturn(List.of());

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByUser(99);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("Should not include soft-deleted comments in user comment list")
        void getCommentsByUser_doesNotIncludeDeletedComments() {

            // Given — repository method already filters deleted ones out
            Integer authorId = 5;
            // Only 1 active comment returned (deleted ones filtered by repo query)
            when(commentRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId))
                    .thenReturn(List.of(buildComment(1, 10, authorId, null, false)));

            // When
            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByUser(authorId);

            // Then
            assertThat(response.getData()).hasSize(1);
            assertThat(response.getData().get(0).getIsDeleted()).isFalse();
        }
    }
}