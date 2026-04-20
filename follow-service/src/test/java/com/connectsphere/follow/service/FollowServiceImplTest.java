package com.connectsphere.follow.service;

import com.connectsphere.follow.dto.*;
import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.exception.AlreadyFollowingException;
import com.connectsphere.follow.exception.FollowNotFoundException;
import com.connectsphere.follow.exception.SelfFollowException;
import com.connectsphere.follow.repository.FollowRepository;
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
 * FollowServiceImplTest - Unit tests for Follow Service business logic
 *
 * Uses Mockito to mock FollowRepository.
 * Tests cover:
 *  - follow (success, self-follow, already following)
 *  - unfollow (success, not following)
 *  - isFollowing (true, false)
 *  - getFollowers / getFollowing
 *  - getFollowerCount / getFollowingCount / getFollowCounts
 *  - getMutualFollows
 *  - getSuggestedUsers
 *  - getFolloweeIds
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FollowServiceImpl Unit Tests")
class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private FollowServiceImpl followService;

    // ── Test Data ─────────────────────────────────────────────────────────────

    private Follow buildFollow(Integer followId, Integer followerId, Integer followeeId) {
        return Follow.builder()
                .followId(followId)
                .followerId(followerId)
                .followeeId(followeeId)
                .status(FollowStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── follow() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("follow()")
    class FollowTests {

        @Test
        @DisplayName("Should create follow relationship successfully")
        void follow_success() {
            // Given
            Integer followerId = 1;
            Integer followeeId = 2;
            Follow saved = buildFollow(10, followerId, followeeId);

            when(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId))
                    .thenReturn(false);
            when(followRepository.save(any(Follow.class))).thenReturn(saved);

            // When
            ApiResponseDTO<FollowResponseDTO> response =
                    followService.follow(followerId, followeeId);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Followed successfully");
            assertThat(response.getData().getFollowerId()).isEqualTo(followerId);
            assertThat(response.getData().getFolloweeId()).isEqualTo(followeeId);
            assertThat(response.getData().getStatus()).isEqualTo(FollowStatus.ACTIVE);
            verify(followRepository).save(any(Follow.class));
        }

        @Test
        @DisplayName("Should throw SelfFollowException when user tries to follow themselves")
        void follow_selfFollow_throwsException() {
            // When / Then
            assertThatThrownBy(() -> followService.follow(1, 1))
                    .isInstanceOf(SelfFollowException.class)
                    .hasMessageContaining("cannot follow yourself");

            verify(followRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AlreadyFollowingException on duplicate follow")
        void follow_alreadyFollowing_throwsException() {
            // Given
            when(followRepository.existsByFollowerIdAndFolloweeId(1, 2)).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> followService.follow(1, 2))
                    .isInstanceOf(AlreadyFollowingException.class)
                    .hasMessageContaining("already following");

            verify(followRepository, never()).save(any());
        }
    }

    // ── unfollow() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unfollow()")
    class UnfollowTests {

        @Test
        @DisplayName("Should unfollow successfully when follow exists")
        void unfollow_success() {
            // Given
            Follow existing = buildFollow(10, 1, 2);
            when(followRepository.findByFollowerIdAndFolloweeId(1, 2))
                    .thenReturn(Optional.of(existing));

            // When
            ApiResponseDTO<String> response = followService.unfollow(1, 2);

            // Then
            assertThat(response.isSuccess()).isTrue();;
            assertThat(response.getMessage()).isEqualTo("Unfollowed successfully");
            verify(followRepository).deleteByFollowerIdAndFolloweeId(1, 2);
        }

        @Test
        @DisplayName("Should throw FollowNotFoundException when not currently following")
        void unfollow_notFollowing_throwsException() {
            // Given
            when(followRepository.findByFollowerIdAndFolloweeId(1, 99))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> followService.unfollow(1, 99))
                    .isInstanceOf(FollowNotFoundException.class)
                    .hasMessageContaining("not following");

            verify(followRepository, never()).deleteByFollowerIdAndFolloweeId(any(), any());
        }
    }

    // ── isFollowing() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isFollowing()")
    class IsFollowingTests {

        @Test
        @DisplayName("Should return true when ACTIVE follow exists")
        void isFollowing_true() {
            when(followRepository.existsByFollowerIdAndFolloweeIdAndStatus(1, 2, FollowStatus.ACTIVE))
                    .thenReturn(true);

            ApiResponseDTO<Boolean> response = followService.isFollowing(1, 2);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isTrue();
        }

        @Test
        @DisplayName("Should return false when no follow relationship exists")
        void isFollowing_false() {
            when(followRepository.existsByFollowerIdAndFolloweeIdAndStatus(1, 99, FollowStatus.ACTIVE))
                    .thenReturn(false);

            ApiResponseDTO<Boolean> response = followService.isFollowing(1, 99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isFalse();
        }
    }

    // ── getFollowers() / getFollowing() ───────────────────────────────────────

    @Nested
    @DisplayName("getFollowers() and getFollowing()")
    class FollowListTests {

        @Test
        @DisplayName("Should return list of followers")
        void getFollowers_returnsList() {
            List<Follow> followers = List.of(
                    buildFollow(1, 10, 5),
                    buildFollow(2, 11, 5)
            );
            when(followRepository.findByFolloweeIdAndStatus(5, FollowStatus.ACTIVE))
                    .thenReturn(followers);

            ApiResponseDTO<List<FollowResponseDTO>> response = followService.getFollowers(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData()).allMatch(dto -> dto.getFolloweeId().equals(5));
        }

        @Test
        @DisplayName("Should return empty list when user has no followers")
        void getFollowers_noFollowers_emptyList() {
            when(followRepository.findByFolloweeIdAndStatus(99, FollowStatus.ACTIVE))
                    .thenReturn(List.of());

            ApiResponseDTO<List<FollowResponseDTO>> response = followService.getFollowers(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("Should return list of users this person follows")
        void getFollowing_returnsList() {
            List<Follow> following = List.of(
                    buildFollow(1, 5, 10),
                    buildFollow(2, 5, 11)
            );
            when(followRepository.findByFollowerIdAndStatus(5, FollowStatus.ACTIVE))
                    .thenReturn(following);

            ApiResponseDTO<List<FollowResponseDTO>> response = followService.getFollowing(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData()).allMatch(dto -> dto.getFollowerId().equals(5));
        }
    }

    // ── getFollowCounts() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFollowCounts()")
    class FollowCountTests {

        @Test
        @DisplayName("Should return combined follower and following counts")
        void getFollowCounts_returnsBoth() {
            when(followRepository.countByFolloweeIdAndStatus(5, FollowStatus.ACTIVE)).thenReturn(100);
            when(followRepository.countByFollowerIdAndStatus(5, FollowStatus.ACTIVE)).thenReturn(50);

            ApiResponseDTO<FollowCountDTO> response = followService.getFollowCounts(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getUserId()).isEqualTo(5);
            assertThat(response.getData().getFollowerCount()).isEqualTo(100);
            assertThat(response.getData().getFollowingCount()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should return zero counts for new user")
        void getFollowCounts_newUser_zeroCounts() {
            when(followRepository.countByFolloweeIdAndStatus(99, FollowStatus.ACTIVE)).thenReturn(0);
            when(followRepository.countByFollowerIdAndStatus(99, FollowStatus.ACTIVE)).thenReturn(0);

            ApiResponseDTO<FollowCountDTO> response = followService.getFollowCounts(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getFollowerCount()).isZero();
            assertThat(response.getData().getFollowingCount()).isZero();
        }
    }

    // ── getMutualFollows() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMutualFollows()")
    class MutualFollowsTests {

        @Test
        @DisplayName("Should return list of mutual connection userIds")
        void getMutualFollows_returnsMutuals() {
            when(followRepository.findMutualFollows(1)).thenReturn(List.of(2, 3, 4));

            ApiResponseDTO<List<Integer>> response = followService.getMutualFollows(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsExactly(2, 3, 4);
        }

        @Test
        @DisplayName("Should return empty list when no mutuals exist")
        void getMutualFollows_noMutuals_emptyList() {
            when(followRepository.findMutualFollows(99)).thenReturn(List.of());

            ApiResponseDTO<List<Integer>> response = followService.getMutualFollows(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── getSuggestedUsers() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getSuggestedUsers()")
    class SuggestedUsersTests {

        @Test
        @DisplayName("Should return second-degree connection userIds")
        void getSuggestedUsers_returnsSuggestions() {
            when(followRepository.findSuggestedUsers(1)).thenReturn(List.of(5, 6, 7));

            ApiResponseDTO<List<Integer>> response = followService.getSuggestedUsers(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsExactly(5, 6, 7);
        }

        @Test
        @DisplayName("Should return empty list when no suggestions available")
        void getSuggestedUsers_noSuggestions_emptyList() {
            when(followRepository.findSuggestedUsers(99)).thenReturn(List.of());

            ApiResponseDTO<List<Integer>> response = followService.getSuggestedUsers(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    // ── getFolloweeIds() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFolloweeIds()")
    class FolloweeIdsTests {

        @Test
        @DisplayName("Should return list of followee IDs for feed building")
        void getFolloweeIds_returnsList() {
            when(followRepository.findFolloweeIdsByFollowerId(1))
                    .thenReturn(List.of(2, 3, 4, 5));

            ApiResponseDTO<List<Integer>> response = followService.getFolloweeIds(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsExactly(2, 3, 4, 5);
        }

        @Test
        @DisplayName("Should return empty list when user follows nobody")
        void getFolloweeIds_notFollowingAnyone_emptyList() {
            when(followRepository.findFolloweeIdsByFollowerId(99)).thenReturn(List.of());

            ApiResponseDTO<List<Integer>> response = followService.getFolloweeIds(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }
}