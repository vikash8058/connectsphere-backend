package com.connectsphere.comment.service;

import com.connectsphere.comment.client.PostClient;
import com.connectsphere.comment.dto.*;
import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.exception.CommentNotFoundException;
import com.connectsphere.comment.exception.PostNotFoundException;
import com.connectsphere.comment.exception.PostServiceUnavailableException;
import com.connectsphere.comment.exception.UnauthorizedActionException;
import com.connectsphere.comment.repository.CommentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentServiceImpl Unit Tests")
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostClient postClient;          // Feign interface

    @Mock
    private RabbitTemplate rabbitTemplate;  // async notifications

    @InjectMocks
    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        // Inject @Value fields that Spring normally sets from application.yml
        ReflectionTestUtils.setField(commentService, "notificationExchange", "test.exchange");
        ReflectionTestUtils.setField(commentService, "notificationRoutingKey", "test.key");
    }

    // ── Test Data ───

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
     * Builds a valid PostApiResponse mock — simulates post-service returning a live post.
     * postAuthorId is the ID of the user who created the post (notification recipient).
     */
    private PostApiResponse buildValidPostResponse(Integer postAuthorId) {
        PostResponseDTO postData = new PostResponseDTO();
        postData.setAuthorId(postAuthorId);
        postData.setIsDeleted(false);

        PostApiResponse response = new PostApiResponse();
        response.setSuccess(true);
        response.setData(postData);
        return response;
    }

    // ── addComment ──

    @Nested
    @DisplayName("addComment()")
    class AddCommentTests {

        @Test
        @DisplayName("Should add a top-level comment successfully")
        void addComment_topLevel_success() {
            // Given
            Integer authorId = 1;
            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(10)
                    .parentCommentId(null)
                    .content("Great post!")
                    .build();

            // Mock: post exists and is not deleted, author is userId=5
            when(postClient.getPostById(10)).thenReturn(buildValidPostResponse(5));
            // Mock: increment is fire-and-forget
            doNothing().when(postClient).incrementCommentCount(10);

            Comment saved = buildComment(100, 10, authorId, null, false);
            saved.setContent("Great post!");
            when(commentRepository.save(any(Comment.class))).thenReturn(saved);

            // When
            ApiResponseDTO<CommentResponseDTO> response =
                    commentService.addComment(authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment added successfully");
            assertThat(response.getData().getPostId()).isEqualTo(10);
            assertThat(response.getData().getAuthorId()).isEqualTo(authorId);
            assertThat(response.getData().getParentCommentId()).isNull();
            assertThat(response.getData().getContent()).isEqualTo("Great post!");
            assertThat(response.getData().getIsDeleted()).isFalse();

            verify(commentRepository).save(any(Comment.class));
            verify(postClient).getPostById(10);
            verify(postClient).incrementCommentCount(10);
        }

        @Test
        @DisplayName("Should add a reply to an existing comment successfully")
        void addComment_reply_success() {
            // Given
            Integer authorId = 2;
            Integer parentCommentId = 50;
            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(10)
                    .parentCommentId(parentCommentId)
                    .content("Nice point!")
                    .build();

            when(postClient.getPostById(10)).thenReturn(buildValidPostResponse(5));
            doNothing().when(postClient).incrementCommentCount(10);

            // Parent comment exists and is not deleted
            Comment parentComment = buildComment(parentCommentId, 10, 1, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(parentCommentId))
                    .thenReturn(Optional.of(parentComment));

            Comment savedReply = buildComment(101, 10, authorId, parentCommentId, false);
            savedReply.setContent("Nice point!");
            when(commentRepository.save(any(Comment.class))).thenReturn(savedReply);

            // When
            ApiResponseDTO<CommentResponseDTO> response =
                    commentService.addComment(authorId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getParentCommentId()).isEqualTo(parentCommentId);
            assertThat(response.getData().getContent()).isEqualTo("Nice point!");

            verify(commentRepository).findByCommentIdAndIsDeletedFalse(parentCommentId);
            verify(commentRepository).save(any(Comment.class));
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException when replying to deleted parent")
        void addComment_replyToDeletedParent_throwsException() {
            // Given
            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(10)
                    .parentCommentId(999)
                    .content("Reply to deleted comment")
                    .build();

            when(postClient.getPostById(10)).thenReturn(buildValidPostResponse(5));
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> commentService.addComment(1, request))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessageContaining("999");

            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PostNotFoundException when post does not exist")
        void addComment_postNotFound_throwsException() {
            // Given
            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(999)
                    .parentCommentId(null)
                    .content("Comment on missing post")
                    .build();

            // Feign returns 404 equivalent — null/failed response
            PostApiResponse notFound = new PostApiResponse();
            notFound.setSuccess(false);
            notFound.setData(null);
            when(postClient.getPostById(999)).thenReturn(notFound);

            // When / Then
            assertThatThrownBy(() -> commentService.addComment(1, request))
                    .isInstanceOf(PostNotFoundException.class);

            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PostServiceUnavailableException when post-service is down")
        void addComment_postServiceDown_throwsException() {
            // Given
            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(10)
                    .parentCommentId(null)
                    .content("Comment when service is down")
                    .build();

            // Feign throws when post-service is completely unreachable
            when(postClient.getPostById(10))
                    .thenThrow(new RuntimeException("Connection refused"));

            // When / Then — verifyPostExists() catches this and throws 503
            assertThatThrownBy(() -> commentService.addComment(1, request))
                    .isInstanceOf(PostServiceUnavailableException.class);

            // Comment must NOT be saved — post verification failed
            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should still save comment when incrementCommentCount Feign call fails")
        void addComment_incrementFails_commentStillSaved() {
            // Given — post exists fine, but the counter increment call fails
            AddCommentRequestDTO request = AddCommentRequestDTO.builder()
                    .postId(10)
                    .parentCommentId(null)
                    .content("Comment despite counter failure")
                    .build();

            when(postClient.getPostById(10)).thenReturn(buildValidPostResponse(5));

            // Increment is fire-and-forget — failure is swallowed, comment still saved
            doThrow(new RuntimeException("Counter service error")).when(postClient).incrementCommentCount(10);

            Comment saved = buildComment(102, 10, 1, null, false);
            when(commentRepository.save(any(Comment.class))).thenReturn(saved);

            // When — should NOT throw; graceful degradation on counter failure only
            ApiResponseDTO<CommentResponseDTO> response =
                    commentService.addComment(1, request);

            // Then — comment is saved despite counter increment failure
            assertThat(response.isSuccess()).isTrue();
            verify(commentRepository).save(any(Comment.class));
        }
    }

    // ── getCommentsByPost ──

    @Nested
    @DisplayName("getCommentsByPost()")
    class GetCommentsByPostTests {

        @Test
        @DisplayName("Should return all comments including deleted ones with placeholder")
        void getCommentsByPost_includesDeletedWithPlaceholder() {
            // Given
            Integer postId = 10;
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
            assertThat(response.getData().get(0).getContent())
                    .isEqualTo("Test comment content");
            assertThat(response.getData().get(0).getIsDeleted()).isFalse();
            assertThat(response.getData().get(1).getContent())
                    .isEqualTo("[This comment was deleted]");
            assertThat(response.getData().get(1).getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("Should return empty list when post has no comments")
        void getCommentsByPost_noComments_emptyList() {
            when(commentRepository.findByPostIdOrderByCreatedAtAsc(99))
                    .thenReturn(List.of());

            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByPost(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── getTopLevelComments ──

    @Nested
    @DisplayName("getTopLevelComments()")
    class GetTopLevelCommentsTests {

        @Test
        @DisplayName("Should return only top-level comments")
        void getTopLevelComments_returnsOnlyTopLevel() {
            Integer postId = 10;
            Comment c1 = buildComment(1, postId, 1, null, false);
            Comment c2 = buildComment(2, postId, 2, null, false);
            when(commentRepository.findTopLevelByPostId(postId))
                    .thenReturn(List.of(c1, c2));

            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getTopLevelComments(postId);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData())
                    .allMatch(dto -> dto.getParentCommentId() == null);
        }
    }

    // ── getCommentById ─

    @Nested
    @DisplayName("getCommentById()")
    class GetCommentByIdTests {

        @Test
        @DisplayName("Should return comment when found")
        void getCommentById_found_success() {
            Comment comment = buildComment(1, 10, 1, null, false);
            when(commentRepository.findByCommentId(1)).thenReturn(Optional.of(comment));

            ApiResponseDTO<CommentResponseDTO> response = commentService.getCommentById(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getCommentId()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException when comment not found")
        void getCommentById_notFound_throwsException() {
            when(commentRepository.findByCommentId(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.getCommentById(999))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("Should return deleted comment with placeholder content")
        void getCommentById_deletedComment_returnsPlaceholder() {
            Comment deleted = buildComment(5, 10, 1, null, true);
            when(commentRepository.findByCommentId(5)).thenReturn(Optional.of(deleted));

            ApiResponseDTO<CommentResponseDTO> response = commentService.getCommentById(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getContent())
                    .isEqualTo("[This comment was deleted]");
            assertThat(response.getData().getIsDeleted()).isTrue();
        }
    }

    // ── getReplies ──

    @Nested
    @DisplayName("getReplies()")
    class GetRepliesTests {

        @Test
        @DisplayName("Should return all replies for a parent comment")
        void getReplies_returnsReplies() {
            Integer parentId = 10;
            List<Comment> replies = List.of(
                    buildComment(20, 1, 2, parentId, false),
                    buildComment(21, 1, 3, parentId, false)
            );
            when(commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentId))
                    .thenReturn(replies);

            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getReplies(parentId);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData())
                    .allMatch(dto -> dto.getParentCommentId().equals(parentId));
        }

        @Test
        @DisplayName("Should return empty list when comment has no replies")
        void getReplies_noReplies_emptyList() {
            when(commentRepository.findByParentCommentIdOrderByCreatedAtAsc(99))
                    .thenReturn(List.of());

            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getReplies(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── updateComment ───

    @Nested
    @DisplayName("updateComment()")
    class UpdateCommentTests {

        @Test
        @DisplayName("Should update comment content when requester is the author")
        void updateComment_ownComment_success() {
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

            ApiResponseDTO<CommentResponseDTO> response =
                    commentService.updateComment(commentId, authorId, request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment updated successfully");
            assertThat(response.getData().getContent()).isEqualTo("Updated content");
            verify(commentRepository).save(any(Comment.class));
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-author tries to update")
        void updateComment_notOwner_throwsException() {
            Comment comment = buildComment(1, 5, 10, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));

            assertThatThrownBy(() -> commentService.updateComment(
                    1, 99, UpdateCommentRequestDTO.builder().content("hack").build()))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("not authorized");

            verify(commentRepository, never()).save(any());
        }
    }

    // ── deleteComment ───

    @Nested
    @DisplayName("deleteComment()")
    class DeleteCommentTests {

        @Test
        @DisplayName("Should soft-delete comment when requester is the author")
        void deleteComment_ownComment_success() {
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));
            doNothing().when(postClient).decrementCommentCount(10);

            ApiResponseDTO<String> response = commentService.deleteComment(1, 5, "USER");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment deleted successfully");
            verify(commentRepository).softDeleteByCommentId(1);
            verify(postClient).decrementCommentCount(10);
        }

        @Test
        @DisplayName("Should allow ADMIN to delete any comment")
        void deleteComment_adminDeletesAny_success() {
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));
            doNothing().when(postClient).decrementCommentCount(10);

            ApiResponseDTO<String> response = commentService.deleteComment(1, 99, "ADMIN");

            assertThat(response.isSuccess()).isTrue();
            verify(commentRepository).softDeleteByCommentId(1);
        }

        @Test
        @DisplayName("Should allow MODERATOR to delete any comment")
        void deleteComment_moderatorDeletesAny_success() {
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));
            doNothing().when(postClient).decrementCommentCount(10);
            ApiResponseDTO<String> response = commentService.deleteComment(1, 88, "MODERATOR");

            assertThat(response.isSuccess()).isTrue();
            verify(commentRepository).softDeleteByCommentId(1);
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-author USER tries to delete")
        void deleteComment_notOwner_throwsException() {
            Comment comment = buildComment(1, 10, 5, null, false);
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(comment));

            assertThatThrownBy(() -> commentService.deleteComment(1, 77, "USER"))
                    .isInstanceOf(UnauthorizedActionException.class);

            verify(commentRepository, never()).softDeleteByCommentId(anyInt());
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException when comment not found on delete")
        void deleteComment_notFound_throwsException() {
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.deleteComment(999, 1, "USER"))
                    .isInstanceOf(CommentNotFoundException.class);
        }
    }

    // ── likeComment / unlikeComment ───

    @Nested
    @DisplayName("likeComment() and unlikeComment()")
    class LikeUnlikeCommentTests {

        @Test
        @DisplayName("Should like a comment successfully")
        void likeComment_existingComment_success() {
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(buildComment(1, 10, 1, null, false)));

            ApiResponseDTO<String> response = commentService.likeComment(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment liked successfully");
            verify(commentRepository).incrementLikes(1);
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException on likeComment for missing comment")
        void likeComment_commentNotFound_throwsException() {
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.likeComment(999))
                    .isInstanceOf(CommentNotFoundException.class);

            verify(commentRepository, never()).incrementLikes(anyInt());
        }

        @Test
        @DisplayName("Should unlike a comment successfully")
        void unlikeComment_existingComment_success() {
            when(commentRepository.findByCommentIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(buildComment(1, 10, 1, null, false)));

            ApiResponseDTO<String> response = commentService.unlikeComment(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Comment unliked successfully");
            verify(commentRepository).decrementLikes(1);
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException on unlikeComment for missing comment")
        void unlikeComment_commentNotFound_throwsException() {
            when(commentRepository.findByCommentIdAndIsDeletedFalse(999))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.unlikeComment(999))
                    .isInstanceOf(CommentNotFoundException.class);

            verify(commentRepository, never()).decrementLikes(anyInt());
        }
    }

    // ── getCommentCount ───

    @Nested
    @DisplayName("getCommentCount()")
    class GetCommentCountTests {

        @Test
        @DisplayName("Should return correct comment count for a post")
        void getCommentCount_returnsCount() {
            when(commentRepository.countByPostIdAndIsDeletedFalse(10)).thenReturn(7);

            ApiResponseDTO<Integer> response = commentService.getCommentCount(10);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(7);
        }

        @Test
        @DisplayName("Should return zero when post has no comments")
        void getCommentCount_noComments_returnsZero() {
            when(commentRepository.countByPostIdAndIsDeletedFalse(99)).thenReturn(0);

            ApiResponseDTO<Integer> response = commentService.getCommentCount(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isZero();
        }
    }

    // ── getCommentsByUser ─

    @Nested
    @DisplayName("getCommentsByUser()")
    class GetCommentsByUserTests {

        @Test
        @DisplayName("Should return all active comments by a user")
        void getCommentsByUser_returnsList() {
            Integer authorId = 5;
            List<Comment> comments = List.of(
                    buildComment(1, 10, authorId, null, false),
                    buildComment(2, 20, authorId, 1, false)
            );
            when(commentRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId))
                    .thenReturn(comments);

            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByUser(authorId);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData())
                    .allMatch(dto -> dto.getAuthorId().equals(authorId));
        }

        @Test
        @DisplayName("Should return empty list when user has made no comments")
        void getCommentsByUser_noComments_emptyList() {
            when(commentRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(99))
                    .thenReturn(List.of());

            ApiResponseDTO<List<CommentResponseDTO>> response =
                    commentService.getCommentsByUser(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }
}