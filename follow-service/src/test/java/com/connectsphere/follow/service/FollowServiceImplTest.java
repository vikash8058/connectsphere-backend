package com.connectsphere.follow.service;

import com.connectsphere.follow.client.AuthServiceClient;
import com.connectsphere.follow.dto.*;
import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.exception.AlreadyFollowingException;
import com.connectsphere.follow.exception.FollowNotFoundException;
import com.connectsphere.follow.exception.SelfFollowException;
import com.connectsphere.follow.exception.UserNotFoundException;
import com.connectsphere.follow.repository.FollowRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("FollowServiceImpl Unit Tests")
class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private FollowServiceImpl followService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(followService, "notificationExchange", "notification.exchange");
        ReflectionTestUtils.setField(followService, "notificationRoutingKey", "notification.key");
    }

    // ── Test Data ───

    private Follow buildFollow(Integer followId, Integer followerId, Integer followeeId) {
        return Follow.builder()
                .followId(followId)
                .followerId(followerId)
                .followeeId(followeeId)
                .status(FollowStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UserExistsResponseDTO buildValidUserResponse() {
        UserDataDTO userData = new UserDataDTO();
        UserExistsResponseDTO response = new UserExistsResponseDTO();
        response.setSuccess(true);
        response.setData(userData);
        return response;
    }

    // ── follow() ────

    @Nested
    @DisplayName("follow()")
    class FollowTests {

        @Test
        @DisplayName("Should create follow relationship successfully")
        void follow_success() {
            Integer followerId = 1;
            Integer followeeId = 2;
            Follow saved = buildFollow(10, followerId, followeeId);

            when(authServiceClient.getUserById(eq(followeeId), any()))
                    .thenReturn(buildValidUserResponse());
            when(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId))
                    .thenReturn(false);
            when(followRepository.save(any(Follow.class))).thenReturn(saved);

            ApiResponseDTO<FollowResponseDTO> response =
                    followService.follow(followerId, followeeId);

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
            assertThatThrownBy(() -> followService.follow(1, 1))
                    .isInstanceOf(SelfFollowException.class)
                    .hasMessageContaining("cannot follow yourself");

            verify(followRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AlreadyFollowingException on duplicate follow")
        void follow_alreadyFollowing_throwsException() {
            when(authServiceClient.getUserById(eq(2), any()))
                    .thenReturn(buildValidUserResponse());
            when(followRepository.existsByFollowerIdAndFolloweeId(1, 2)).thenReturn(true);

            assertThatThrownBy(() -> followService.follow(1, 2))
                    .isInstanceOf(AlreadyFollowingException.class)
                    .hasMessageContaining("already following");

            verify(followRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user response is null")
        void follow_userResponseNull_throwsUserNotFoundException() {
            when(authServiceClient.getUserById(eq(2), any())).thenReturn(null);

            assertThatThrownBy(() -> followService.follow(1, 2))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when auth service throws exception")
        void follow_authServiceThrows_throwsUserNotFoundException() {
            when(authServiceClient.getUserById(eq(2), any()))
                    .thenThrow(new RuntimeException("Service down"));

            assertThatThrownBy(() -> followService.follow(1, 2))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should still save follow when RabbitMQ publish fails")
        void follow_rabbitFails_stillSavesFollow() {
            Follow saved = buildFollow(10, 1, 2);

            when(authServiceClient.getUserById(eq(2), any()))
                    .thenReturn(buildValidUserResponse());
            when(followRepository.existsByFollowerIdAndFolloweeId(1, 2)).thenReturn(false);
            when(followRepository.save(any(Follow.class))).thenReturn(saved);
            doThrow(new RuntimeException("RabbitMQ down"))
                    .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

            ApiResponseDTO<FollowResponseDTO> response = followService.follow(1, 2);

            assertThat(response.isSuccess()).isTrue();
            verify(followRepository).save(any(Follow.class));
        }
    }

    // ── unfollow() ──
    @Nested
    @DisplayName("unfollow()")
    class UnfollowTests {

        @Test
        @DisplayName("Should unfollow successfully when follow exists")
        void unfollow_success() {
            Follow existing = buildFollow(10, 1, 2);

            when(authServiceClient.getUserById(eq(2), any()))
                    .thenReturn(buildValidUserResponse());
            when(followRepository.findByFollowerIdAndFolloweeId(1, 2))
                    .thenReturn(Optional.of(existing));

            ApiResponseDTO<String> response = followService.unfollow(1, 2);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Unfollowed successfully");
            verify(followRepository).deleteByFollowerIdAndFolloweeId(1, 2);
        }

        @Test
        @DisplayName("Should throw FollowNotFoundException when not currently following")
        void unfollow_notFollowing_throwsException() {
            when(authServiceClient.getUserById(eq(99), any()))
                    .thenReturn(buildValidUserResponse());
            when(followRepository.findByFollowerIdAndFolloweeId(1, 99))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> followService.unfollow(1, 99))
                    .isInstanceOf(FollowNotFoundException.class)
                    .hasMessageContaining("not following");

            verify(followRepository, never()).deleteByFollowerIdAndFolloweeId(any(), any());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found on unfollow")
        void unfollow_userNotFound_throwsException() {
            when(authServiceClient.getUserById(eq(99), any()))
                    .thenThrow(new RuntimeException("Service down"));

            assertThatThrownBy(() -> followService.unfollow(1, 99))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ── isFollowing() ──

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

    // ── getFollowers() / getFollowing() ────
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

    // ── getFollowCounts() ───

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

    // ── getMutualFollows() ────
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

    // ── getSuggestedUsers() ──

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

    // ── getFolloweeIds() ───
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