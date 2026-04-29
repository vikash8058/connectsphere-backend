package com.connectsphere.like.service;

import com.connectsphere.like.dto.*;
import com.connectsphere.like.entity.Like;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.exception.AlreadyLikedException;
import com.connectsphere.like.exception.LikeNotFoundException;
import com.connectsphere.like.client.CommentServiceClient;
import com.connectsphere.like.client.PostServiceClient;
import com.connectsphere.like.repository.LikeRepository;
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
 * LikeServiceImplTest - Unit tests for Like Service business logic
 *
 * Uses Mockito to mock:
 *   - LikeRepository        → no DB needed
 *   - PostServiceClient     → no actual Feign HTTP call
 *   - CommentServiceClient  → no actual Feign HTTP call
 *
 * Tests cover:
 *  - likeTarget (POST success, COMMENT success, already liked)
 *  - unlikeTarget (success, not found)
 *  - hasLiked (true, false)
 *  - getLikesByTarget
 *  - getLikesByUser
 *  - getLikeCount
 *  - getLikeCountByType (valid, invalid reactionType)
 *  - getReactionSummary
 *  - changeReaction (success, no existing reaction)
 *  - Feign counter sync (post increment, comment increment, graceful failure)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LikeServiceImpl Unit Tests")
class LikeServiceImplTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private PostServiceClient postServiceClient;

    @Mock
    private CommentServiceClient commentServiceClient;

    @InjectMocks
    private LikeServiceImpl likeService;

    // ── Test Data ──

    private Like buildLike(Integer likeId, Integer userId, Integer targetId,
                            TargetType targetType, ReactionType reactionType) {
        return Like.builder()
                .likeId(likeId)
                .userId(userId)
                .targetId(targetId)
                .targetType(targetType)
                .reactionType(reactionType)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── likeTarget() ─

    @Nested
    @DisplayName("likeTarget()")
    class LikeTargetTests {

        @Test
        @DisplayName("Should react to a POST successfully and call post-service increment via Feign")
        void likeTarget_post_success() {

            // Given
            Integer userId = 1;
            LikeRequestDTO request = LikeRequestDTO.builder()
                    .targetId(10)
                    .targetType(TargetType.POST)
                    .reactionType(ReactionType.LIKE)
                    .build();

            Like saved = buildLike(100, userId, 10, TargetType.POST, ReactionType.LIKE);

            when(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    userId, 10, TargetType.POST)).thenReturn(false);
            when(likeRepository.save(any(Like.class))).thenReturn(saved);

            // When
            ApiResponseDTO<LikeResponseDTO> response = likeService.likeTarget(userId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Reaction added successfully");
            assertThat(response.getData().getUserId()).isEqualTo(userId);
            assertThat(response.getData().getTargetId()).isEqualTo(10);
            assertThat(response.getData().getTargetType()).isEqualTo(TargetType.POST);
            assertThat(response.getData().getReactionType()).isEqualTo(ReactionType.LIKE);

            verify(likeRepository).save(any(Like.class));
            // Feign post-service increment must be called
            verify(postServiceClient).incrementLikeCount(10);
            // Comment-service must NOT be called
            verify(commentServiceClient, never()).incrementLikeCount(any());
        }

        @Test
        @DisplayName("Should react to a COMMENT successfully and call comment-service increment via Feign")
        void likeTarget_comment_success() {

            // Given
            Integer userId = 2;
            LikeRequestDTO request = LikeRequestDTO.builder()
                    .targetId(20)
                    .targetType(TargetType.COMMENT)
                    .reactionType(ReactionType.LOVE)
                    .build();

            Like saved = buildLike(101, userId, 20, TargetType.COMMENT, ReactionType.LOVE);

            when(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    userId, 20, TargetType.COMMENT)).thenReturn(false);
            when(likeRepository.save(any(Like.class))).thenReturn(saved);

            // When
            ApiResponseDTO<LikeResponseDTO> response = likeService.likeTarget(userId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getTargetType()).isEqualTo(TargetType.COMMENT);
            assertThat(response.getData().getReactionType()).isEqualTo(ReactionType.LOVE);

            verify(likeRepository).save(any(Like.class));
            // Feign comment-service increment must be called
            verify(commentServiceClient).incrementLikeCount(20);
            // Post-service must NOT be called
            verify(postServiceClient, never()).incrementLikeCount(any());
        }

        @Test
        @DisplayName("Should default reactionType to LIKE when not provided")
        void likeTarget_defaultsToLike() {

            // Given
            Integer userId = 3;
            LikeRequestDTO request = LikeRequestDTO.builder()
                    .targetId(30)
                    .targetType(TargetType.POST)
                    .reactionType(null)   // not provided
                    .build();

            Like saved = buildLike(102, userId, 30, TargetType.POST, ReactionType.LIKE);

            when(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    userId, 30, TargetType.POST)).thenReturn(false);
            when(likeRepository.save(any(Like.class))).thenReturn(saved);

            // When
            ApiResponseDTO<LikeResponseDTO> response = likeService.likeTarget(userId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getReactionType()).isEqualTo(ReactionType.LIKE);
        }

        @Test
        @DisplayName("Should throw AlreadyLikedException on duplicate reaction")
        void likeTarget_alreadyLiked_throwsException() {

            // Given
            Integer userId = 1;
            LikeRequestDTO request = LikeRequestDTO.builder()
                    .targetId(10)
                    .targetType(TargetType.POST)
                    .reactionType(ReactionType.LIKE)
                    .build();

            when(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    userId, 10, TargetType.POST)).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> likeService.likeTarget(userId, request))
                    .isInstanceOf(AlreadyLikedException.class)
                    .hasMessageContaining("already reacted");

            verify(likeRepository, never()).save(any());
            verify(postServiceClient, never()).incrementLikeCount(any());
        }

        @Test
        @DisplayName("Should still save like when Feign post-service call fails (graceful degradation)")
        void likeTarget_feignPostServiceDown_likeStillSaved() {

            // Given
            Integer userId = 5;
            LikeRequestDTO request = LikeRequestDTO.builder()
                    .targetId(50)
                    .targetType(TargetType.POST)
                    .reactionType(ReactionType.WOW)
                    .build();

            Like saved = buildLike(103, userId, 50, TargetType.POST, ReactionType.WOW);

            when(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    userId, 50, TargetType.POST)).thenReturn(false);
            when(likeRepository.save(any(Like.class))).thenReturn(saved);
            // Simulate post-service Feign failure
            doThrow(new RuntimeException("Connection refused"))
                    .when(postServiceClient).incrementLikeCount(50);

            // When — should NOT throw; graceful degradation
            ApiResponseDTO<LikeResponseDTO> response = likeService.likeTarget(userId, request);

            // Then — like is saved despite Feign failure
            assertThat(response.isSuccess()).isTrue();
            verify(likeRepository).save(any(Like.class));
        }

        @Test
        @DisplayName("Should still save like when Feign comment-service call fails (graceful degradation)")
        void likeTarget_feignCommentServiceDown_likeStillSaved() {

            // Given
            Integer userId = 6;
            LikeRequestDTO request = LikeRequestDTO.builder()
                    .targetId(60)
                    .targetType(TargetType.COMMENT)
                    .reactionType(ReactionType.HAHA)
                    .build();

            Like saved = buildLike(104, userId, 60, TargetType.COMMENT, ReactionType.HAHA);

            when(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    userId, 60, TargetType.COMMENT)).thenReturn(false);
            when(likeRepository.save(any(Like.class))).thenReturn(saved);
            doThrow(new RuntimeException("Connection refused"))
                    .when(commentServiceClient).incrementLikeCount(60);

            // When
            ApiResponseDTO<LikeResponseDTO> response = likeService.likeTarget(userId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(likeRepository).save(any(Like.class));
        }
    }

    // ── unlikeTarget() ───

    @Nested
    @DisplayName("unlikeTarget()")
    class UnlikeTargetTests {

        @Test
        @DisplayName("Should remove POST reaction and call post-service decrement via Feign")
        void unlikeTarget_post_success() {

            // Given
            Integer userId = 1;
            Like existing = buildLike(100, userId, 10, TargetType.POST, ReactionType.LIKE);

            when(likeRepository.findByUserIdAndTargetIdAndTargetType(
                    userId, 10, TargetType.POST)).thenReturn(Optional.of(existing));

            // When
            ApiResponseDTO<String> response =
                    likeService.unlikeTarget(userId, 10, TargetType.POST);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Reaction removed successfully");

            verify(likeRepository).deleteByUserIdAndTargetIdAndTargetType(
                    userId, 10, TargetType.POST);
            // Feign decrement must be called
            verify(postServiceClient).decrementLikeCount(10);
            verify(commentServiceClient, never()).decrementLikeCount(any());
        }

        @Test
        @DisplayName("Should remove COMMENT reaction and call comment-service decrement via Feign")
        void unlikeTarget_comment_success() {

            // Given
            Integer userId = 2;
            Like existing = buildLike(101, userId, 20, TargetType.COMMENT, ReactionType.LOVE);

            when(likeRepository.findByUserIdAndTargetIdAndTargetType(
                    userId, 20, TargetType.COMMENT)).thenReturn(Optional.of(existing));

            // When
            ApiResponseDTO<String> response =
                    likeService.unlikeTarget(userId, 20, TargetType.COMMENT);

            // Then
            assertThat(response.isSuccess()).isTrue();
            verify(likeRepository).deleteByUserIdAndTargetIdAndTargetType(
                    userId, 20, TargetType.COMMENT);
            verify(commentServiceClient).decrementLikeCount(20);
            verify(postServiceClient, never()).decrementLikeCount(any());
        }

        @Test
        @DisplayName("Should throw LikeNotFoundException when no reaction exists to remove")
        void unlikeTarget_noReactionExists_throwsException() {

            // Given
            when(likeRepository.findByUserIdAndTargetIdAndTargetType(
                    1, 999, TargetType.POST)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> likeService.unlikeTarget(1, 999, TargetType.POST))
                    .isInstanceOf(LikeNotFoundException.class)
                    .hasMessageContaining("No reaction found");

            verify(likeRepository, never()).deleteByUserIdAndTargetIdAndTargetType(
                    any(), any(), any());
            verify(postServiceClient, never()).decrementLikeCount(any());
        }

        @Test
        @DisplayName("Should still delete like when Feign decrement fails (graceful degradation)")
        void unlikeTarget_feignFails_likeStillDeleted() {

            // Given
            Integer userId = 3;
            Like existing = buildLike(105, userId, 30, TargetType.POST, ReactionType.SAD);

            when(likeRepository.findByUserIdAndTargetIdAndTargetType(
                    userId, 30, TargetType.POST)).thenReturn(Optional.of(existing));
            doThrow(new RuntimeException("post-service down"))
                    .when(postServiceClient).decrementLikeCount(30);

            // When — should NOT throw
            ApiResponseDTO<String> response =
                    likeService.unlikeTarget(userId, 30, TargetType.POST);

            // Then — unlike completes despite Feign failure
            assertThat(response.isSuccess()).isTrue();
            verify(likeRepository).deleteByUserIdAndTargetIdAndTargetType(
                    userId, 30, TargetType.POST);
        }
    }

    // ── hasLiked() ────

    @Nested
    @DisplayName("hasLiked()")
    class HasLikedTests {

        @Test
        @DisplayName("Should return true when user has an active reaction")
        void hasLiked_reactionExists_returnsTrue() {

            when(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    1, 10, TargetType.POST)).thenReturn(true);

            ApiResponseDTO<Boolean> response = likeService.hasLiked(1, 10, TargetType.POST);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isTrue();
        }

        @Test
        @DisplayName("Should return false when user has not reacted")
        void hasLiked_noReaction_returnsFalse() {

            when(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    1, 99, TargetType.COMMENT)).thenReturn(false);

            ApiResponseDTO<Boolean> response = likeService.hasLiked(1, 99, TargetType.COMMENT);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isFalse();
        }
    }

    // ── getLikesByTarget() ───

    @Nested
    @DisplayName("getLikesByTarget()")
    class GetLikesByTargetTests {

        @Test
        @DisplayName("Should return all reactions on a post")
        void getLikesByTarget_post_returnsList() {

            List<Like> likes = List.of(
                    buildLike(1, 1, 10, TargetType.POST, ReactionType.LIKE),
                    buildLike(2, 2, 10, TargetType.POST, ReactionType.LOVE),
                    buildLike(3, 3, 10, TargetType.POST, ReactionType.WOW)
            );
            when(likeRepository.findByTargetIdAndTargetType(10, TargetType.POST))
                    .thenReturn(likes);

            ApiResponseDTO<List<LikeResponseDTO>> response =
                    likeService.getLikesByTarget(10, TargetType.POST);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(3);
            assertThat(response.getData())
                    .allMatch(dto -> dto.getTargetId().equals(10))
                    .allMatch(dto -> dto.getTargetType() == TargetType.POST);
        }

        @Test
        @DisplayName("Should return empty list when no reactions on target")
        void getLikesByTarget_noReactions_emptyList() {

            when(likeRepository.findByTargetIdAndTargetType(99, TargetType.COMMENT))
                    .thenReturn(List.of());

            ApiResponseDTO<List<LikeResponseDTO>> response =
                    likeService.getLikesByTarget(99, TargetType.COMMENT);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── getLikesByUser() ─

    @Nested
    @DisplayName("getLikesByUser()")
    class GetLikesByUserTests {

        @Test
        @DisplayName("Should return all reactions made by a user across posts and comments")
        void getLikesByUser_returnsList() {

            List<Like> likes = List.of(
                    buildLike(1, 5, 10, TargetType.POST, ReactionType.LIKE),
                    buildLike(2, 5, 20, TargetType.COMMENT, ReactionType.HAHA)
            );
            when(likeRepository.findByUserId(5)).thenReturn(likes);

            ApiResponseDTO<List<LikeResponseDTO>> response = likeService.getLikesByUser(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData()).allMatch(dto -> dto.getUserId().equals(5));
        }

        @Test
        @DisplayName("Should return empty list when user has made no reactions")
        void getLikesByUser_noReactions_emptyList() {

            when(likeRepository.findByUserId(99)).thenReturn(List.of());

            ApiResponseDTO<List<LikeResponseDTO>> response = likeService.getLikesByUser(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── getLikeCount() ───

    @Nested
    @DisplayName("getLikeCount()")
    class GetLikeCountTests {

        @Test
        @DisplayName("Should return total reaction count for a post")
        void getLikeCount_post_returnsCount() {

            when(likeRepository.countByTargetIdAndTargetType(10, TargetType.POST))
                    .thenReturn(17);

            ApiResponseDTO<Integer> response = likeService.getLikeCount(10, TargetType.POST);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(17);
        }

        @Test
        @DisplayName("Should return zero when no reactions on target")
        void getLikeCount_noReactions_returnsZero() {

            when(likeRepository.countByTargetIdAndTargetType(99, TargetType.COMMENT))
                    .thenReturn(0);

            ApiResponseDTO<Integer> response = likeService.getLikeCount(99, TargetType.COMMENT);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isZero();
        }
    }

    // ── getLikeCountByType() ───

    @Nested
    @DisplayName("getLikeCountByType()")
    class GetLikeCountByTypeTests {

        @Test
        @DisplayName("Should return count for a specific reaction type")
        void getLikeCountByType_validType_returnsCount() {

            when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.LOVE)).thenReturn(5);

            ApiResponseDTO<Integer> response =
                    likeService.getLikeCountByType(10, TargetType.POST, "LOVE");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(5);
            verify(likeRepository).countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.LOVE);
        }

        @Test
        @DisplayName("Should be case-insensitive for reactionType input")
        void getLikeCountByType_lowercaseInput_works() {

            when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.HAHA)).thenReturn(3);

            ApiResponseDTO<Integer> response =
                    likeService.getLikeCountByType(10, TargetType.POST, "haha");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid reactionType")
        void getLikeCountByType_invalidType_throwsException() {

            assertThatThrownBy(() ->
                    likeService.getLikeCountByType(10, TargetType.POST, "THUMBSUP"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid reactionType")
                    .hasMessageContaining("LIKE, LOVE, HAHA, WOW, SAD, ANGRY");
        }
    }

    // ── getReactionSummary() ──

    @Nested
    @DisplayName("getReactionSummary()")
    class GetReactionSummaryTests {

        @Test
        @DisplayName("Should return full emoji bar map with all 6 reaction types")
        void getReactionSummary_returnsAllSixTypes() {

            // Given — mock each type count
            when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.LIKE)).thenReturn(10);
            when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.LOVE)).thenReturn(5);
            when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.HAHA)).thenReturn(2);
            when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.WOW)).thenReturn(1);
            when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.SAD)).thenReturn(0);
            when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                    10, TargetType.POST, ReactionType.ANGRY)).thenReturn(0);

            // When
            ApiResponseDTO<ReactionSummaryDTO> response =
                    likeService.getReactionSummary(10, TargetType.POST);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getTargetId()).isEqualTo(10);
            assertThat(response.getData().getTargetType()).isEqualTo("POST");
            assertThat(response.getData().getTotalCount()).isEqualTo(18);
            assertThat(response.getData().getReactions()).containsKey("LIKE");
            assertThat(response.getData().getReactions()).containsKey("LOVE");
            assertThat(response.getData().getReactions()).containsKey("HAHA");
            assertThat(response.getData().getReactions()).containsKey("WOW");
            assertThat(response.getData().getReactions()).containsKey("SAD");
            assertThat(response.getData().getReactions()).containsKey("ANGRY");
            assertThat(response.getData().getReactions().get("LIKE")).isEqualTo(10);
            assertThat(response.getData().getReactions().get("LOVE")).isEqualTo(5);
            assertThat(response.getData().getReactions().get("HAHA")).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return zero totalCount when no reactions exist")
        void getReactionSummary_noReactions_zeroTotal() {

            // All reaction types return 0
            for (ReactionType type : ReactionType.values()) {
                when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(
                        99, TargetType.COMMENT, type)).thenReturn(0);
            }

            ApiResponseDTO<ReactionSummaryDTO> response =
                    likeService.getReactionSummary(99, TargetType.COMMENT);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getTotalCount()).isZero();
            assertThat(response.getData().getReactions()).hasSize(6);
        }
    }

    // ── changeReaction() ───

    @Nested
    @DisplayName("changeReaction()")
    class ChangeReactionTests {

        @Test
        @DisplayName("Should change reaction type successfully (LIKE → LOVE)")
        void changeReaction_success() {

            // Given
            Integer userId = 1;
            ChangeReactionRequestDTO request = ChangeReactionRequestDTO.builder()
                    .targetId(10)
                    .targetType(TargetType.POST)
                    .newReactionType(ReactionType.LOVE)
                    .build();

            Like existing = buildLike(100, userId, 10, TargetType.POST, ReactionType.LIKE);
            Like newLike  = buildLike(101, userId, 10, TargetType.POST, ReactionType.LOVE);

            when(likeRepository.findByUserIdAndTargetIdAndTargetType(
                    userId, 10, TargetType.POST)).thenReturn(Optional.of(existing));
            when(likeRepository.save(any(Like.class))).thenReturn(newLike);

            // When
            ApiResponseDTO<LikeResponseDTO> response = likeService.changeReaction(userId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Reaction changed successfully");
            assertThat(response.getData().getReactionType()).isEqualTo(ReactionType.LOVE);

            // Old reaction deleted, new one saved
            verify(likeRepository).deleteByUserIdAndTargetIdAndTargetType(
                    userId, 10, TargetType.POST);
            verify(likeRepository).save(any(Like.class));

            // Counter must NOT change (still 1 reaction — no sync needed)
            verify(postServiceClient, never()).incrementLikeCount(any());
            verify(postServiceClient, never()).decrementLikeCount(any());
        }

        @Test
        @DisplayName("Should change COMMENT reaction successfully (HAHA → ANGRY)")
        void changeReaction_comment_success() {

            // Given
            Integer userId = 2;
            ChangeReactionRequestDTO request = ChangeReactionRequestDTO.builder()
                    .targetId(20)
                    .targetType(TargetType.COMMENT)
                    .newReactionType(ReactionType.ANGRY)
                    .build();

            Like existing = buildLike(102, userId, 20, TargetType.COMMENT, ReactionType.HAHA);
            Like newLike  = buildLike(103, userId, 20, TargetType.COMMENT, ReactionType.ANGRY);

            when(likeRepository.findByUserIdAndTargetIdAndTargetType(
                    userId, 20, TargetType.COMMENT)).thenReturn(Optional.of(existing));
            when(likeRepository.save(any(Like.class))).thenReturn(newLike);

            // When
            ApiResponseDTO<LikeResponseDTO> response = likeService.changeReaction(userId, request);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getReactionType()).isEqualTo(ReactionType.ANGRY);

            verify(likeRepository).deleteByUserIdAndTargetIdAndTargetType(
                    userId, 20, TargetType.COMMENT);
            verify(likeRepository).save(any(Like.class));
            // No counter change
            verify(commentServiceClient, never()).incrementLikeCount(any());
            verify(commentServiceClient, never()).decrementLikeCount(any());
        }

        @Test
        @DisplayName("Should throw LikeNotFoundException when no existing reaction to change")
        void changeReaction_noExistingReaction_throwsException() {

            // Given
            ChangeReactionRequestDTO request = ChangeReactionRequestDTO.builder()
                    .targetId(99)
                    .targetType(TargetType.POST)
                    .newReactionType(ReactionType.WOW)
                    .build();

            when(likeRepository.findByUserIdAndTargetIdAndTargetType(
                    1, 99, TargetType.POST)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> likeService.changeReaction(1, request))
                    .isInstanceOf(LikeNotFoundException.class)
                    .hasMessageContaining("No existing reaction found");

            verify(likeRepository, never()).deleteByUserIdAndTargetIdAndTargetType(
                    any(), any(), any());
            verify(likeRepository, never()).save(any());
        }
    }
}