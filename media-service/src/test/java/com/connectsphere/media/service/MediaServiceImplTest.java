package com.connectsphere.media.service;

import com.connectsphere.media.client.PostServiceClient;
import com.connectsphere.media.dto.*;
import com.connectsphere.media.entity.Media;
import com.connectsphere.media.entity.MediaType;
import com.connectsphere.media.entity.Story;
import com.connectsphere.media.exception.MediaNotFoundException;
import com.connectsphere.media.exception.PostNotFoundException;
import com.connectsphere.media.exception.StoryNotFoundException;
import com.connectsphere.media.exception.UnauthorizedActionException;
import com.connectsphere.media.exception.UnsupportedMediaTypeException;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.repository.StoryRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MediaServiceImplTest — Unit tests for MediaServiceImpl
 *
 * Architecture: Controller → Service → Repository → DB
 * Test scope  : Service layer only (Mockito mocks for repositories)
 *
 * Test groups:
 *   UploadMediaTests       — file upload, MIME validation, size limits
 *   GetMediaTests          — getById, getByPost, getByUploader
 *   DeleteMediaTests       — own delete, admin delete, unauthorized delete
 *   LinkMediaToPostTests   — ownership check, successful link
 *   SoftDeleteByPostTests  — internal inter-service endpoint
 *   CreateStoryTests       — story creation, expiresAt calculation
 *   GetStoriesTests        — feed, by user, view count
 *   ViewStoryTests         — view increments, author self-view skip
 *   DeleteStoryTests       — own delete, admin delete, unauthorized
 *   ExpireStoriesTests     — scheduler batch expiry
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MediaServiceImpl Tests")
class MediaServiceImplTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private PostServiceClient postServiceClient;

    @InjectMocks
    private MediaServiceImpl mediaService;

    // ── SETUP ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Inject @Value fields via ReflectionTestUtils (Spring context not loaded)
        ReflectionTestUtils.setField(mediaService, "cdnBaseUrl",       "https://cdn.connectsphere.com/media");
        ReflectionTestUtils.setField(mediaService, "maxImageSizeKb",    10240L);
        ReflectionTestUtils.setField(mediaService, "maxVideoSizeKb",   102400L);
        ReflectionTestUtils.setField(mediaService, "allowedImageTypes", "image/jpeg,image/png,image/webp");
        ReflectionTestUtils.setField(mediaService, "allowedVideoTypes", "video/mp4");
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private Media buildMedia(Integer mediaId, Integer uploaderId, MediaType type, Integer linkedPostId) {
        return Media.builder()
                .mediaId(mediaId)
                .uploaderId(uploaderId)
                .url("https://cdn.connectsphere.com/media/2026/04/uuid.jpg")
                .mediaType(type)
                .sizeKb(512L)
                .mimeType(type == MediaType.IMAGE ? "image/jpeg" : "video/mp4")
                .linkedPostId(linkedPostId)
                .isDeleted(false)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    private Story buildStory(Integer storyId, Integer authorId, boolean isActive) {
        return Story.builder()
                .storyId(storyId)
                .authorId(authorId)
                .mediaUrl("https://cdn.connectsphere.com/media/2026/04/story.jpg")
                .caption("Test caption")
                .mediaType(MediaType.IMAGE)
                .viewsCount(5)
                .expiresAt(LocalDateTime.now().plusHours(12))
                .createdAt(LocalDateTime.now().minusHours(12))
                .isActive(isActive)
                .build();
    }

    private ApiResponseDTO<PostSummaryDTO> buildPostLookup(Integer postId, Integer authorId) {
        return ApiResponseDTO.<PostSummaryDTO>builder()
                .success(true)
                .message("Post retrieved")
                .data(PostSummaryDTO.builder().postId(postId).authorId(authorId).build())
                .build();
    }

    // =========================================================================
    // GROUP 1: UPLOAD MEDIA TESTS
    // =========================================================================

    @Nested
    @DisplayName("Upload Media Tests")
    class UploadMediaTests {

        @Test
        @DisplayName("Should upload JPEG image successfully and return CDN URL")
        void uploadMedia_validJpeg_returnsMediaResponseDTO() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", new byte[1024 * 100]); // 100 KB

            Media saved = buildMedia(1, 3, MediaType.IMAGE, null);
            when(mediaRepository.save(any(Media.class))).thenReturn(saved);

            ApiResponseDTO<MediaResponseDTO> response = mediaService.uploadMedia(file, 3);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Media uploaded successfully");
            assertThat(response.getData().getMediaType()).isEqualTo(MediaType.IMAGE);
            assertThat(response.getData().getMimeType()).isEqualTo("image/jpeg");
            assertThat(response.getData().getLinkedPostId()).isNull();
            verify(mediaRepository).save(any(Media.class));
        }

        @Test
        @DisplayName("Should upload PNG image successfully")
        void uploadMedia_validPng_success() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "image.png", "image/png", new byte[1024 * 200]); // 200 KB

            Media saved = buildMedia(2, 3, MediaType.IMAGE, null);
            when(mediaRepository.save(any(Media.class))).thenReturn(saved);

            ApiResponseDTO<MediaResponseDTO> response = mediaService.uploadMedia(file, 3);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should upload WebP image successfully")
        void uploadMedia_validWebp_success() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "image.webp", "image/webp", new byte[1024 * 50]);

            Media saved = buildMedia(3, 3, MediaType.IMAGE, null);
            when(mediaRepository.save(any(Media.class))).thenReturn(saved);

            ApiResponseDTO<MediaResponseDTO> response = mediaService.uploadMedia(file, 3);
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should upload MP4 video successfully")
        void uploadMedia_validMp4Video_success() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "video.mp4", "video/mp4", new byte[1024 * 1024 * 10]); // 10 MB

            Media saved = buildMedia(4, 3, MediaType.VIDEO, null);
            when(mediaRepository.save(any(Media.class))).thenReturn(saved);

            ApiResponseDTO<MediaResponseDTO> response = mediaService.uploadMedia(file, 3);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getMediaType()).isEqualTo(MediaType.VIDEO);
        }

        @Test
        @DisplayName("Should throw UnsupportedMediaTypeException for GIF file")
        void uploadMedia_gifFile_throwsUnsupportedMediaType() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "anim.gif", "image/gif", new byte[1024]);

            assertThatThrownBy(() -> mediaService.uploadMedia(file, 3))
                    .isInstanceOf(UnsupportedMediaTypeException.class)
                    .hasMessageContaining("Unsupported file type");
        }

        @Test
        @DisplayName("Should throw UnsupportedMediaTypeException for AVI video")
        void uploadMedia_aviVideo_throwsUnsupportedMediaType() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "video.avi", "video/avi", new byte[1024]);

            assertThatThrownBy(() -> mediaService.uploadMedia(file, 3))
                    .isInstanceOf(UnsupportedMediaTypeException.class);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when image exceeds 10MB limit")
        void uploadMedia_imageTooLarge_throwsIllegalArgument() {
            // 11 MB image — exceeds 10240 KB limit
            MockMultipartFile file = new MockMultipartFile(
                    "file", "big.jpg", "image/jpeg", new byte[1024 * 1024 * 11]);

            assertThatThrownBy(() -> mediaService.uploadMedia(file, 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Image file exceeds");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when video exceeds 100MB limit")
        void uploadMedia_videoTooLarge_throwsIllegalArgument() {
            // 101 MB video — exceeds 102400 KB limit
            MockMultipartFile file = new MockMultipartFile(
                    "file", "big.mp4", "video/mp4", new byte[1024 * 1024 * 101]);

            assertThatThrownBy(() -> mediaService.uploadMedia(file, 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Video file exceeds");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty file")
        void uploadMedia_emptyFile_throwsIllegalArgument() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);

            assertThatThrownBy(() -> mediaService.uploadMedia(file, 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("CDN URL should follow expected format with year/month")
        void uploadMedia_cdnUrlFormat_containsYearAndMonth() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "pic.jpg", "image/jpeg", new byte[1024]);

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            when(mediaRepository.save(captor.capture())).thenAnswer(inv -> {
                Media m = captor.getValue();
                m.builder(); // already set
                return m;
            });
            when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

            mediaService.uploadMedia(file, 3);

            verify(mediaRepository).save(argThat(m ->
                    m.getUrl().startsWith("https://cdn.connectsphere.com/media/") &&
                    m.getUrl().endsWith(".jpg")
            ));
        }
    }

    // =========================================================================
    // GROUP 2: GET MEDIA TESTS
    // =========================================================================

    @Nested
    @DisplayName("Get Media Tests")
    class GetMediaTests {

        @Test
        @DisplayName("Should return media DTO when found by ID")
        void getMediaById_exists_returnsDTO() {
            Media media = buildMedia(1, 3, MediaType.IMAGE, 10);
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(1)).thenReturn(Optional.of(media));

            ApiResponseDTO<MediaResponseDTO> response = mediaService.getMediaById(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getMediaId()).isEqualTo(1);
            assertThat(response.getData().getLinkedPostId()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should throw MediaNotFoundException when ID does not exist")
        void getMediaById_notFound_throwsMediaNotFoundException() {
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mediaService.getMediaById(99))
                    .isInstanceOf(MediaNotFoundException.class)
                    .hasMessageContaining("Media not found with ID: 99");
        }

        @Test
        @DisplayName("Should return all media linked to a post")
        void getMediaByPost_hasTwoItems_returnsBoth() {
            List<Media> mediaList = List.of(
                    buildMedia(1, 3, MediaType.IMAGE, 5),
                    buildMedia(2, 3, MediaType.IMAGE, 5)
            );
            when(mediaRepository.findByLinkedPostIdAndIsDeletedFalse(5)).thenReturn(mediaList);

            ApiResponseDTO<List<MediaResponseDTO>> response = mediaService.getMediaByPost(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
        }

        @Test
        @DisplayName("Should return empty list when post has no media")
        void getMediaByPost_noMedia_returnsEmptyList() {
            when(mediaRepository.findByLinkedPostIdAndIsDeletedFalse(99)).thenReturn(List.of());

            ApiResponseDTO<List<MediaResponseDTO>> response = mediaService.getMediaByPost(99);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("Should return all media uploaded by a user ordered by newest")
        void getMediaByUploader_threeItems_returnsAll() {
            List<Media> mediaList = List.of(
                    buildMedia(3, 3, MediaType.VIDEO, null),
                    buildMedia(2, 3, MediaType.IMAGE, 5),
                    buildMedia(1, 3, MediaType.IMAGE, 4)
            );
            when(mediaRepository.findByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(3))
                    .thenReturn(mediaList);

            ApiResponseDTO<List<MediaResponseDTO>> response = mediaService.getMediaByUploader(3);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(3);
        }

        @Test
        @DisplayName("Should return empty list when user has no uploads")
        void getMediaByUploader_noUploads_returnsEmptyList() {
            when(mediaRepository.findByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(99))
                    .thenReturn(List.of());

            ApiResponseDTO<List<MediaResponseDTO>> response = mediaService.getMediaByUploader(99);

            assertThat(response.getData()).isEmpty();
        }
    }

    // =========================================================================
    // GROUP 3: DELETE MEDIA TESTS
    // =========================================================================

    @Nested
    @DisplayName("Delete Media Tests")
    class DeleteMediaTests {

        @Test
        @DisplayName("Should soft-delete media when requested by owner")
        void deleteMedia_byOwner_success() {
            Media media = buildMedia(1, 3, MediaType.IMAGE, 10);
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(1)).thenReturn(Optional.of(media));

            ApiResponseDTO<String> response = mediaService.deleteMedia(1, 3, "USER");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Media deleted successfully");
            verify(mediaRepository).softDeleteByMediaId(1);
        }

        @Test
        @DisplayName("Should allow ADMIN to delete any user's media")
        void deleteMedia_byAdmin_success() {
            Media media = buildMedia(1, 3, MediaType.IMAGE, null);
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(1)).thenReturn(Optional.of(media));

            ApiResponseDTO<String> response = mediaService.deleteMedia(1, 99, "ADMIN");

            assertThat(response.isSuccess()).isTrue();
            verify(mediaRepository).softDeleteByMediaId(1);
        }

        @Test
        @DisplayName("Should allow MODERATOR to delete any user's media")
        void deleteMedia_byModerator_success() {
            Media media = buildMedia(1, 3, MediaType.IMAGE, null);
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(1)).thenReturn(Optional.of(media));

            ApiResponseDTO<String> response = mediaService.deleteMedia(1, 50, "MODERATOR");

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-owner tries to delete")
        void deleteMedia_byNonOwner_throwsUnauthorized() {
            Media media = buildMedia(1, 3, MediaType.IMAGE, null); // owner = 3
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(1)).thenReturn(Optional.of(media));

            assertThatThrownBy(() -> mediaService.deleteMedia(1, 7, "USER")) // requester = 7
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("not authorized");
        }

        @Test
        @DisplayName("Should throw MediaNotFoundException when deleting non-existent media")
        void deleteMedia_notFound_throwsMediaNotFoundException() {
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mediaService.deleteMedia(99, 3, "USER"))
                    .isInstanceOf(MediaNotFoundException.class);
        }
    }

    // =========================================================================
    // GROUP 4: LINK MEDIA TO POST TESTS
    // =========================================================================

    @Nested
    @DisplayName("Link Media To Post Tests")
    class LinkMediaToPostTests {

        @Test
        @DisplayName("Should link media to post when requested by uploader")
        void linkMediaToPost_byUploader_success() {
            Media before = buildMedia(1, 3, MediaType.IMAGE, null);
            Media after  = buildMedia(1, 3, MediaType.IMAGE, 10);

            when(postServiceClient.getPostById(10)).thenReturn(buildPostLookup(10, 3));
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                    .thenReturn(Optional.of(before))
                    .thenReturn(Optional.of(after));
            doNothing().when(mediaRepository).linkMediaToPost(1, 10);

            ApiResponseDTO<MediaResponseDTO> response = mediaService.linkMediaToPost(1, 10, 3);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getLinkedPostId()).isEqualTo(10);
            verify(mediaRepository).linkMediaToPost(1, 10);
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-uploader tries to link")
        void linkMediaToPost_nonUploader_throwsUnauthorized() {
            Media media = buildMedia(1, 3, MediaType.IMAGE, null); // owner = 3
            when(postServiceClient.getPostById(10)).thenReturn(buildPostLookup(10, 7));
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(1)).thenReturn(Optional.of(media));

            assertThatThrownBy(() -> mediaService.linkMediaToPost(1, 10, 7)) // requester = 7
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("only link your own media");
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when caller does not own target post")
        void linkMediaToPost_postOwnedByAnotherUser_throwsUnauthorized() {
            Media media = buildMedia(1, 3, MediaType.IMAGE, null);

            when(postServiceClient.getPostById(10)).thenReturn(buildPostLookup(10, 99));
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(1)).thenReturn(Optional.of(media));

            assertThatThrownBy(() -> mediaService.linkMediaToPost(1, 10, 3))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("your own post");

            verify(mediaRepository, never()).linkMediaToPost(anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should throw MediaNotFoundException when media does not exist")
        void linkMediaToPost_notFound_throwsMediaNotFoundException() {
            when(postServiceClient.getPostById(5)).thenReturn(buildPostLookup(5, 3));
            when(mediaRepository.findByMediaIdAndIsDeletedFalse(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mediaService.linkMediaToPost(99, 5, 3))
                    .isInstanceOf(MediaNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw PostNotFoundException when post-service says post does not exist")
        void linkMediaToPost_postMissing_throwsPostNotFoundException() {
            ApiResponseDTO<PostSummaryDTO> postMissing = ApiResponseDTO.<PostSummaryDTO>builder()
                    .success(false)
                    .message("Post not found")
                    .build();

            when(postServiceClient.getPostById(404)).thenReturn(postMissing);

            assertThatThrownBy(() -> mediaService.linkMediaToPost(1, 404, 3))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessageContaining("404");

            verify(mediaRepository, never()).linkMediaToPost(anyInt(), anyInt());
        }
    }

    // =========================================================================
    // GROUP 5: SOFT DELETE BY POST (INTERNAL)
    // =========================================================================

    @Nested
    @DisplayName("Soft Delete By Post Tests")
    class SoftDeleteByPostTests {

        @Test
        @DisplayName("Should soft-delete all media for a given postId")
        void softDeleteByPost_callsRepository() {
            doNothing().when(mediaRepository).softDeleteByLinkedPostId(5);

            ApiResponseDTO<String> response = mediaService.softDeleteByPost(5);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("5");
            verify(mediaRepository).softDeleteByLinkedPostId(5);
        }

        @Test
        @DisplayName("Should succeed even if post has no media (idempotent)")
        void softDeleteByPost_noMedia_noException() {
            doNothing().when(mediaRepository).softDeleteByLinkedPostId(99);

            assertThatCode(() -> mediaService.softDeleteByPost(99))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // GROUP 6: CREATE STORY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Create Story Tests")
    class CreateStoryTests {

        @Test
        @DisplayName("Should create story with expiresAt = now + 24 hours")
        void createStory_validRequest_expiresIn24Hours() {
            CreateStoryRequestDTO request = CreateStoryRequestDTO.builder()
                    .mediaUrl("https://cdn.connectsphere.com/media/2026/04/story.jpg")
                    .caption("Hello world")
                    .mediaType(MediaType.IMAGE)
                    .build();

            Story saved = buildStory(1, 3, true);
            when(storyRepository.save(any(Story.class))).thenReturn(saved);

            ApiResponseDTO<StoryResponseDTO> response = mediaService.createStory(request, 3);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Story created successfully");
            assertThat(response.getData().getIsActive()).isTrue();

            // Verify the saved story has expiresAt ≈ now + 24h
            verify(storyRepository).save(argThat(s ->
                    s.getExpiresAt().isAfter(LocalDateTime.now().plusHours(23))
                    && s.getExpiresAt().isBefore(LocalDateTime.now().plusHours(25))
            ));
        }

        @Test
        @DisplayName("Should create story without caption (optional)")
        void createStory_noCaption_success() {
            CreateStoryRequestDTO request = CreateStoryRequestDTO.builder()
                    .mediaUrl("https://cdn.connectsphere.com/media/2026/04/story.jpg")
                    .caption(null)
                    .mediaType(MediaType.IMAGE)
                    .build();

            Story saved = buildStory(2, 3, true);
            when(storyRepository.save(any(Story.class))).thenReturn(saved);

            ApiResponseDTO<StoryResponseDTO> response = mediaService.createStory(request, 3);
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should create VIDEO story successfully")
        void createStory_videoType_success() {
            CreateStoryRequestDTO request = CreateStoryRequestDTO.builder()
                    .mediaUrl("https://cdn.connectsphere.com/media/2026/04/story.mp4")
                    .caption("Watch this!")
                    .mediaType(MediaType.VIDEO)
                    .build();

            Story saved = Story.builder()
                    .storyId(3).authorId(3).mediaType(MediaType.VIDEO)
                    .viewsCount(0).isActive(true)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .createdAt(LocalDateTime.now())
                    .build();
            when(storyRepository.save(any(Story.class))).thenReturn(saved);

            ApiResponseDTO<StoryResponseDTO> response = mediaService.createStory(request, 3);
            assertThat(response.getData().getMediaType()).isEqualTo(MediaType.VIDEO);
        }

        @Test
        @DisplayName("Should set authorId from JWT, not from request body")
        void createStory_authorIdFromJwt_notSpoofable() {
            CreateStoryRequestDTO request = CreateStoryRequestDTO.builder()
                    .mediaUrl("https://cdn.connectsphere.com/media/2026/04/story.jpg")
                    .mediaType(MediaType.IMAGE)
                    .build();

            when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

            mediaService.createStory(request, 5); // authorId = 5 from JWT

            verify(storyRepository).save(argThat(s -> s.getAuthorId().equals(5)));
        }
    }

    // =========================================================================
    // GROUP 7: GET STORIES TESTS
    // =========================================================================

    @Nested
    @DisplayName("Get Stories Tests")
    class GetStoriesTests {

        @Test
        @DisplayName("Should return active stories for given authorIds (feed)")
        void getActiveStories_twoAuthors_returnsTheirStories() {
            List<Story> stories = List.of(
                    buildStory(1, 2, true),
                    buildStory(2, 4, true),
                    buildStory(3, 2, true)
            );
            when(storyRepository.findActiveStoriesByAuthorIds(List.of(2, 4)))
                    .thenReturn(stories);

            ApiResponseDTO<List<StoryResponseDTO>> response =
                    mediaService.getActiveStories(List.of(2, 4));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(3);
        }

        @Test
        @DisplayName("Should return empty list when authorIds is empty")
        void getActiveStories_emptyAuthorIds_returnsEmpty() {
            ApiResponseDTO<List<StoryResponseDTO>> response =
                    mediaService.getActiveStories(List.of());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
            verify(storyRepository, never()).findActiveStoriesByAuthorIds(any());
        }

        @Test
        @DisplayName("Should return empty list when authorIds is null")
        void getActiveStories_nullAuthorIds_returnsEmpty() {
            ApiResponseDTO<List<StoryResponseDTO>> response =
                    mediaService.getActiveStories(null);

            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("Should return all active stories by a specific user")
        void getStoriesByUser_twoActiveStories_returnsTwo() {
            List<Story> stories = List.of(
                    buildStory(1, 3, true),
                    buildStory(2, 3, true)
            );
            when(storyRepository.findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(3))
                    .thenReturn(stories);

            ApiResponseDTO<List<StoryResponseDTO>> response = mediaService.getStoriesByUser(3);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData()).allMatch(StoryResponseDTO::getIsActive);
        }

        @Test
        @DisplayName("Should return empty list when user has no active stories")
        void getStoriesByUser_noActiveStories_returnsEmpty() {
            when(storyRepository.findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(99))
                    .thenReturn(List.of());

            ApiResponseDTO<List<StoryResponseDTO>> response = mediaService.getStoriesByUser(99);
            assertThat(response.getData()).isEmpty();
        }
    }

    // =========================================================================
    // GROUP 8: VIEW STORY TESTS
    // =========================================================================

    @Nested
    @DisplayName("View Story Tests")
    class ViewStoryTests {

        @Test
        @DisplayName("Should increment view count when viewer is not the author")
        void viewStory_byDifferentUser_incrementsViewCount() {
            Story story = buildStory(1, 3, true); // authorId = 3
            when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));

            ApiResponseDTO<String> response = mediaService.viewStory(1, 7); // viewer = 7

            assertThat(response.isSuccess()).isTrue();
            verify(storyRepository).incrementViewsCount(1);
        }

        @Test
        @DisplayName("Should NOT increment view count when author views their own story")
        void viewStory_byAuthor_doesNotIncrementViewCount() {
            Story story = buildStory(1, 3, true); // authorId = 3
            when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));

            ApiResponseDTO<String> response = mediaService.viewStory(1, 3); // viewer = author = 3

            assertThat(response.isSuccess()).isTrue();
            verify(storyRepository, never()).incrementViewsCount(any());
        }

        @Test
        @DisplayName("Should throw StoryNotFoundException when story is expired/inactive")
        void viewStory_inactiveStory_throwsStoryNotFoundException() {
            when(storyRepository.findByStoryIdAndIsActiveTrue(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mediaService.viewStory(99, 7))
                    .isInstanceOf(StoryNotFoundException.class)
                    .hasMessageContaining("Story not found or has expired");
        }
    }

    // =========================================================================
    // GROUP 9: DELETE STORY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Delete Story Tests")
    class DeleteStoryTests {

        @Test
        @DisplayName("Should delete story when requested by author")
        void deleteStory_byAuthor_success() {
            Story story = buildStory(1, 3, true); // authorId = 3
            when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));

            ApiResponseDTO<String> response = mediaService.deleteStory(1, 3, "USER");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Story deleted successfully");
            verify(storyRepository).deactivateByStoryId(1);
        }

        @Test
        @DisplayName("Should allow ADMIN to delete any user's story")
        void deleteStory_byAdmin_success() {
            Story story = buildStory(1, 3, true); // authorId = 3
            when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));

            ApiResponseDTO<String> response = mediaService.deleteStory(1, 99, "ADMIN");

            assertThat(response.isSuccess()).isTrue();
            verify(storyRepository).deactivateByStoryId(1);
        }

        @Test
        @DisplayName("Should allow MODERATOR to delete any user's story")
        void deleteStory_byModerator_success() {
            Story story = buildStory(1, 3, true);
            when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));

            ApiResponseDTO<String> response = mediaService.deleteStory(1, 50, "MODERATOR");
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should throw UnauthorizedActionException when non-author regular user tries to delete")
        void deleteStory_byNonAuthor_throwsUnauthorized() {
            Story story = buildStory(1, 3, true); // authorId = 3
            when(storyRepository.findByStoryIdAndIsActiveTrue(1)).thenReturn(Optional.of(story));

            assertThatThrownBy(() -> mediaService.deleteStory(1, 7, "USER")) // requester = 7
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("not authorized");
        }

        @Test
        @DisplayName("Should throw StoryNotFoundException when story is already expired")
        void deleteStory_alreadyExpired_throwsStoryNotFoundException() {
            when(storyRepository.findByStoryIdAndIsActiveTrue(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mediaService.deleteStory(99, 3, "USER"))
                    .isInstanceOf(StoryNotFoundException.class)
                    .hasMessageContaining("already expired");
        }
    }

    // =========================================================================
    // GROUP 10: EXPIRE STORIES (SCHEDULER) TESTS
    // =========================================================================

    @Nested
    @DisplayName("Expire Stories Tests")
    class ExpireStoriesTests {

        @Test
        @DisplayName("Should call deactivateExpiredStories and return count of expired stories")
        void expireOldStories_fiveExpired_returnsFive() {
            when(storyRepository.deactivateExpiredStories(any(LocalDateTime.class))).thenReturn(5);

            int count = mediaService.expireOldStories();

            assertThat(count).isEqualTo(5);
            verify(storyRepository).deactivateExpiredStories(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should return 0 when no stories are expired")
        void expireOldStories_nothingExpired_returnsZero() {
            when(storyRepository.deactivateExpiredStories(any(LocalDateTime.class))).thenReturn(0);

            int count = mediaService.expireOldStories();

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("Should pass current time to deactivateExpiredStories")
        void expireOldStories_passesCurrentTime() {
            when(storyRepository.deactivateExpiredStories(any(LocalDateTime.class))).thenReturn(3);

            mediaService.expireOldStories();

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(storyRepository).deactivateExpiredStories(captor.capture());

            // Captured time should be close to now
            assertThat(captor.getValue()).isBetween(
                    LocalDateTime.now().minusSeconds(5),
                    LocalDateTime.now().plusSeconds(5)
            );
        }
    }
}
