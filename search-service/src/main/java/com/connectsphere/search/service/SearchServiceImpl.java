package com.connectsphere.search.service;

import com.connectsphere.search.client.AuthServiceClient;
import com.connectsphere.search.client.PostServiceClient;
import com.connectsphere.search.dto.*;
import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.entity.PostHashtag;
import com.connectsphere.search.exception.HashtagNotFoundException;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final PostServiceClient postServiceClient;
    private final AuthServiceClient authServiceClient;

    /**
     * Regex to extract hashtags from post content.
     * Matches #word tokens — word chars only, min 1 char.
     * e.g. "Hello #spring #boot!" → ["spring", "boot"]
     */
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");

    // ── INDEX POST ──
    /**
     * Index a new post:
     *   1. Parse #tags from content
     *   2. For each tag: upsert Hashtag (insert or increment)
     *   3. Create PostHashtag mapping (with dedup guard)
     *
     * Only PUBLIC posts are indexed — FOLLOWERS_ONLY and PRIVATE
     * posts should not appear in public hashtag feeds.
     */
    @Override
    @Transactional
    public void indexPost(Integer postId, Integer authorId, String content, String visibility) {
        if (content == null || content.isBlank()) {
            log.debug("indexPost skipped — empty content. postId={}", postId);
            return;
        }

        // Only index PUBLIC posts
        if (!"PUBLIC".equalsIgnoreCase(visibility)) {
            log.debug("indexPost skipped — visibility={}. postId={}", visibility, postId);
            return;
        }

        Set<String> tags = extractHashtags(content);

        if (tags.isEmpty()) {
            log.debug("indexPost — no hashtags found in postId={}", postId);
            return;
        }

        log.info("Indexing postId={} with {} hashtag(s): {}", postId, tags.size(), tags);

        for (String tag : tags) {
            Hashtag hashtag = upsertHashtag(tag);
            createPostHashtagMapping(postId, hashtag);
        }
    }

    /**
     * Re-index an updated post:
     *   1. Parse old and new hashtag sets
     *   2. Compute removed = old - new  → delete mappings, decrement counts
     *   3. Compute added   = new - old  → upsert hashtags, create mappings
     *
     * If visibility changed to non-PUBLIC, remove all existing index entries.
     */
    @Override
    @Transactional
    public void reIndexPost(Integer postId, Integer authorId, String newContent,
                            String previousContent, String visibility) {

        // If no longer PUBLIC → remove everything
        if (!"PUBLIC".equalsIgnoreCase(visibility)) {
            log.info("reIndexPost — visibility changed to {}. Removing index for postId={}", visibility, postId);
            removePostIndex(postId);
            return;
        }

        Set<String> oldTags = previousContent != null ? extractHashtags(previousContent) : Collections.emptySet();
        Set<String> newTags = newContent != null ? extractHashtags(newContent) : Collections.emptySet();

        // Tags removed from the post
        Set<String> removedTags = new HashSet<>(oldTags);
        removedTags.removeAll(newTags);

        // Tags added to the post
        Set<String> addedTags = new HashSet<>(newTags);
        addedTags.removeAll(oldTags);

        log.info("reIndexPost postId={} — removed: {}, added: {}", postId, removedTags, addedTags);

        if (!removedTags.isEmpty()) {
            List<PostHashtag> existingMappings = postHashtagRepository.findByPostId(postId);
            for (PostHashtag mapping : existingMappings) {
                if (removedTags.contains(mapping.getHashtag().getTag())) {
                    postHashtagRepository.deleteByPostIdAndHashtagId(
                            postId, mapping.getHashtag().getHashtagId());
                    hashtagRepository.decrementPostCount(mapping.getHashtag().getHashtagId());
                    log.debug("Removed hashtag mapping: postId={} tag={}", postId, mapping.getHashtag().getTag());
                }
            }
        }

        // Add new mappings
        for (String tag : addedTags) {
            Hashtag hashtag = upsertHashtag(tag);
            createPostHashtagMapping(postId, hashtag);
        }
    }

    /**
     * Remove all hashtag mappings for a deleted post.
     * Decrements postCount on each affected Hashtag.
     */
    @Override
    @Transactional
    public void removePostIndex(Integer postId) {
        List<PostHashtag> mappings = postHashtagRepository.findByPostId(postId);

        if (mappings.isEmpty()) {
            log.debug("removePostIndex — no mappings found for postId={}", postId);
            return;
        }

        log.info("removePostIndex postId={} — removing {} hashtag mapping(s)", postId, mappings.size());

        // Decrement postCount for each hashtag
        for (PostHashtag mapping : mappings) {
            hashtagRepository.decrementPostCount(mapping.getHashtag().getHashtagId());
        }

        // Delete all mappings for this post
        postHashtagRepository.deleteByPostId(postId);
    }

    // ── SEARCH POSTS ─────

    /**
     * Full-text keyword search across posts.
     *
     * Strategy:
     *   1. Check if keyword starts with '#' → hashtag search
     *   2. Otherwise → fetch matching postIds from post-service via Feign
     *   3. Enrich each postId with full post details
     *   4. Attach hashtags for each post
     */
    @Override
    public ApiResponseDTO<List<PostSearchResultDTO>> searchPosts(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponseDTO.error("Search keyword cannot be empty");
        }

        String trimmed = keyword.trim();
        List<PostSearchResultDTO> results;

        if (trimmed.startsWith("#")) {
            // Hashtag search — use local index
            String tag = trimmed.substring(1).toLowerCase();
            results = searchByHashtagInternal(tag);
        } else {
            // Keyword search — delegate to post-service
            results = searchByKeywordInternal(trimmed);
        }

        log.info("searchPosts keyword='{}' → {} results", keyword, results.size());
        return ApiResponseDTO.success("Search completed", results);
    }

    /**
     * Search users by username or fullName.
     * Delegates entirely to auth-service via Feign.
     */
    @Override
    public ApiResponseDTO<List<UserSearchResultDTO>> searchUsers(String query) {
        if (query == null || query.isBlank()) {
            return ApiResponseDTO.error("Search query cannot be empty");
        }

        try {
            AuthUserListApiResponse response = authServiceClient.searchUsers(query.trim());

            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("searchUsers — empty response from auth-service for query='{}'", query);
                return ApiResponseDTO.success("No users found", Collections.emptyList());
            }

            List<UserSearchResultDTO> results = response.getData().stream()
                    .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                    .map(this::toUserSearchResult)
                    .collect(Collectors.toList());

            log.info("searchUsers query='{}' → {} results", query, results.size());
            return ApiResponseDTO.success("User search completed", results);

        } catch (Exception e) {
            log.error("searchUsers — auth-service call failed: {}", e.getMessage());
            return ApiResponseDTO.success("User search unavailable", Collections.emptyList());
        }
    }

    // ── HASHTAGS ───
    /**
     * Get all hashtags on a specific post.
     */
    @Override
    public ApiResponseDTO<List<HashtagResponseDTO>> getHashtagsForPost(Integer postId) {
        List<Integer> hashtagIds = postHashtagRepository.findHashtagIdsByPostId(postId);

        if (hashtagIds.isEmpty()) {
            return ApiResponseDTO.success("No hashtags found for this post", Collections.emptyList());
        }

        List<HashtagResponseDTO> hashtags = hashtagIds.stream()
                .map(id -> hashtagRepository.findById(id))
                .filter(Optional::isPresent)
                .map(opt -> toHashtagResponse(opt.get()))
                .collect(Collectors.toList());

        return ApiResponseDTO.success("Hashtags fetched", hashtags);
    }

    /**
     * Get top N trending hashtags ordered by postCount DESC.
     * limit is capped at 50 to prevent abuse.
     */
    @Override
    public ApiResponseDTO<List<HashtagResponseDTO>> getTrendingHashtags(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);

        List<HashtagResponseDTO> trending = hashtagRepository
                .findTrendingHashtags(PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toHashtagResponse)
                .collect(Collectors.toList());

        log.info("getTrendingHashtags limit={} → {} results", safeLimit, trending.size());
        return ApiResponseDTO.success("Trending hashtags fetched", trending);
    }

    /**
     * Get all posts tagged with a specific hashtag.
     * Returns enriched post details fetched from post-service.
     */
    @Override
    public ApiResponseDTO<List<PostSearchResultDTO>> getPostsByHashtag(String tag) {
        if (tag == null || tag.isBlank()) {
            return ApiResponseDTO.error("Hashtag cannot be empty");
        }

        String normalizedTag = tag.toLowerCase().replace("#", "");

        if (!hashtagRepository.existsByTag(normalizedTag)) {
            throw new HashtagNotFoundException("Hashtag not found: #" + normalizedTag);
        }

        List<PostSearchResultDTO> posts = searchByHashtagInternal(normalizedTag);
        return ApiResponseDTO.success("Posts fetched for #" + normalizedTag, posts);
    }

    /**
     * Partial hashtag search / autocomplete.
     */
    @Override
    public ApiResponseDTO<List<HashtagResponseDTO>> searchHashtags(String query) {
        if (query == null || query.isBlank()) {
            return ApiResponseDTO.error("Query cannot be empty");
        }

        String normalizedQuery = query.toLowerCase().replace("#", "");

        List<HashtagResponseDTO> results = hashtagRepository
                .findByTagContainingIgnoreCaseOrderByPostCountDesc(normalizedQuery)
                .stream()
                .map(this::toHashtagResponse)
                .collect(Collectors.toList());

        return ApiResponseDTO.success("Hashtag search completed", results);
    }

    /**
     * Get postCount for a specific hashtag. Returns 0 if not found.
     */
    @Override
    public ApiResponseDTO<Integer> getHashtagCount(String tag) {
        if (tag == null || tag.isBlank()) {
            return ApiResponseDTO.error("Tag cannot be empty");
        }

        String normalizedTag = tag.toLowerCase().replace("#", "");
        Integer count = hashtagRepository.findPostCountByTag(normalizedTag).orElse(0);
        return ApiResponseDTO.success("Hashtag count fetched", count);
    }

    // ── PRIVATE HELPERS ──

    /**
     * Extract all #hashtag tokens from post content.
     * Returns lowercase set (no # prefix).
     * e.g. "Hello #Spring #Boot" → {"spring", "boot"}
     */
    private Set<String> extractHashtags(String content) {
        if (content == null || content.isBlank()) return Collections.emptySet();

        Set<String> tags = new HashSet<>();
        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase();
            if (tag.length() <= 100) {  // guard against absurdly long tags
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * Upsert a Hashtag record:
     *   - If exists → increment postCount
     *   - If not    → insert new with postCount = 1
     */
    @Transactional
    protected Hashtag upsertHashtag(String tag) {
        Optional<Hashtag> existing = hashtagRepository.findByTag(tag);

        if (existing.isPresent()) {
            hashtagRepository.incrementPostCount(existing.get().getHashtagId());
            log.debug("Incremented postCount for tag='{}'", tag);
            return existing.get();
        } else {
            Hashtag newHashtag = Hashtag.builder()
                    .tag(tag)
                    .postCount(1)
                    .build();
            Hashtag saved = hashtagRepository.save(newHashtag);
            log.debug("Created new hashtag: tag='{}' id={}", tag, saved.getHashtagId());
            return saved;
        }
    }

    /**
     * Create a PostHashtag mapping with deduplication guard.
     */
    @Transactional
    protected void createPostHashtagMapping(Integer postId, Hashtag hashtag) {
        boolean exists = postHashtagRepository.existsByPostIdAndHashtagId(
                postId, hashtag.getHashtagId());

        if (!exists) {
            PostHashtag mapping = PostHashtag.builder()
                    .postId(postId)
                    .hashtag(hashtag)
                    .build();
            postHashtagRepository.save(mapping);
            log.debug("Created PostHashtag mapping: postId={} tag='{}'", postId, hashtag.getTag());
        } else {
            log.debug("PostHashtag mapping already exists: postId={} tag='{}'", postId, hashtag.getTag());
        }
    }

    /**
     * Internal: search posts by hashtag tag string.
     * Fetches postIds from local index → enriches via post-service Feign call.
     *
     * Handles stale data: if a post is in the hashtag index but was deleted in post-service,
     * enrichPostResult() returns null and we filter it out. The mapping will be auto-cleaned
     * on the next post update event.
     */
    private List<PostSearchResultDTO> searchByHashtagInternal(String tag) {
        List<Integer> postIds = postHashtagRepository.findPostIdsByHashtagTag(tag);

        if (postIds.isEmpty()) {
            log.debug("searchByHashtagInternal — no postIds found for tag='{}'", tag);
            return Collections.emptyList();
        }

        log.debug("searchByHashtagInternal — found {} postIds for tag='{}'", postIds.size(), tag);

        List<PostSearchResultDTO> results = postIds.stream()
                .map(postId -> {
                    try {
                        PostSearchResultDTO result = enrichPostResult(postId);
                        if (result == null) {
                            log.debug("enrichPostResult returned null for postId={} (post may be deleted or unavailable)", postId);
                        }
                        return result;
                    } catch (Exception e) {
                        log.warn("Error enriching postId={}: {}", postId, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (results.isEmpty() && !postIds.isEmpty()) {
            log.warn("searchByHashtagInternal — {} postIds found for tag='{}' but all were filtered (possibly deleted posts)",
                    postIds.size(), tag);
        }

        return results;
    }

    /**
     * Internal: search posts by keyword via post-service Feign call.
     */
    private List<PostSearchResultDTO> searchByKeywordInternal(String keyword) {
        try {
            var response = postServiceClient.searchPosts(keyword);

            if (response == null || !response.isSuccess() || response.getData() == null) {
                return Collections.emptyList();
            }

            return response.getData().stream()
                    .map(post -> {
                        // Attach hashtags from local index
                        List<String> tags = getTagsForPost(post.getPostId());
                        return PostSearchResultDTO.builder()
                                .postId(post.getPostId())
                                .authorId(post.getAuthorId())
                                .content(post.getContent())
                                .mediaUrls(post.getMediaUrls())
                                .postType(post.getPostType())
                                .visibility(post.getVisibility())
                                .likesCount(post.getLikesCount())
                                .commentsCount(post.getCommentsCount())
                                .sharesCount(post.getSharesCount())
                                .hashtags(tags)
                                .createdAt(post.getCreatedAt())
                                .updatedAt(post.getUpdatedAt())
                                .build();
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("searchByKeywordInternal — post-service call failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Enrich a single postId with full post data from post-service.
     * Returns null if post-service call fails or post is deleted.
     */
    private PostSearchResultDTO enrichPostResult(Integer postId) {
        try {
            PostApiResponse response = postServiceClient.getPostById(postId);

            if (response == null || !response.isSuccess() || response.getData() == null) {
                return null;
            }

            PostDataDTO post = response.getData();

            if (Boolean.TRUE.equals(post.getIsDeleted())) return null;

            List<String> tags = getTagsForPost(postId);

            return PostSearchResultDTO.builder()
                    .postId(post.getPostId())
                    .authorId(post.getAuthorId())
                    .content(post.getContent())
                    .mediaUrls(post.getMediaUrls())
                    .postType(post.getPostType())
                    .visibility(post.getVisibility())
                    .likesCount(post.getLikesCount())
                    .commentsCount(post.getCommentsCount())
                    .sharesCount(post.getSharesCount())
                    .hashtags(tags)
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())
                    .build();

        } catch (Exception e) {
            log.warn("enrichPostResult failed for postId={}: {}", postId, e.getMessage());
            return null;
        }
    }

    /**
     * Get list of tag strings for a post from local index.
     */
    private List<String> getTagsForPost(Integer postId) {
        return postHashtagRepository.findHashtagIdsByPostId(postId).stream()
                .map(hashtagRepository::findById)
                .filter(Optional::isPresent)
                .map(opt -> "#" + opt.get().getTag())
                .collect(Collectors.toList());
    }

    /**
     * Map Hashtag entity → HashtagResponseDTO
     */
    private HashtagResponseDTO toHashtagResponse(Hashtag h) {
        return HashtagResponseDTO.builder()
                .hashtagId(h.getHashtagId())
                .tag(h.getTag())
                .postCount(h.getPostCount())
                .lastUsedAt(h.getLastUsedAt())
                .build();
    }

    /**
     * Map UserDataDTO → UserSearchResultDTO
     */
    private UserSearchResultDTO toUserSearchResult(UserDataDTO u) {
        return UserSearchResultDTO.builder()
                .userId(u.getUserId())
                .username(u.getUsername())
                .fullName(u.getFullName())
                .bio(u.getBio())
                .profilePicUrl(u.getProfilePicUrl())
                .role(u.getRole())
                .build();
    }
}