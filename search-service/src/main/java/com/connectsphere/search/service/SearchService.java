package com.connectsphere.search.service;

import com.connectsphere.search.dto.*;

import java.util.List;

/**
 * SearchService - Business Contract (Interface)
 *
 * Declares all operations as per ConnectSphere case study section 4.8:
 *   indexPost()              - Parse post content, upsert hashtags, create PostHashtag mappings
 *   removePostIndex()        - Remove all hashtag mappings for a deleted post
 *   searchPosts()            - Full-text keyword search across indexed post content
 *   searchUsers()            - Search users by username or fullName via auth-service
 *   getHashtagsForPost()     - Get all hashtags on a specific post
 *   getTrendingHashtags()    - Get top N hashtags by postCount
 *   getPostsByHashtag()      - Get all postIds tagged with a specific hashtag
 *   searchHashtags()         - Partial hashtag search / autocomplete
 *   getHashtagCount()        - Get postCount for a specific hashtag
 */
public interface SearchService {

    /**
     * Index a post — parse #hashtag tokens from content,
     * upsert Hashtag records, create PostHashtag mappings.
     * Called by PostEventListener on POST_CREATED event.
     * Only PUBLIC posts are indexed.
     */
    void indexPost(Integer postId, Integer authorId, String content, String visibility);

    /**
     * Re-index an updated post — diff old vs new hashtags,
     * remove stale mappings, add new ones, adjust postCounts.
     * Called by PostEventListener on POST_UPDATED event.
     */
    void reIndexPost(Integer postId, Integer authorId, String newContent,
                     String previousContent, String visibility);

    /**
     * Remove all hashtag mappings for a deleted post.
     * Decrements postCount on each affected Hashtag.
     * Called by PostEventListener on POST_DELETED event.
     */
    void removePostIndex(Integer postId);

    /**
     * Full-text keyword search across all indexed post content.
     * Searches PostHashtag entries and returns enriched PostSearchResultDTO list.
     * Delegates to post-service via Feign for full post details.
     * Only PUBLIC posts are returned.
     */
    ApiResponseDTO<List<PostSearchResultDTO>> searchPosts(String keyword);

    /**
     * Search users by username or fullName.
     * Delegates to auth-service via Feign.
     * Returns users matching the query string (case-insensitive).
     */
    ApiResponseDTO<List<UserSearchResultDTO>> searchUsers(String query);

    /**
     * Get all hashtags on a specific post.
     * Returns list of HashtagResponseDTO for the given postId.
     */
    ApiResponseDTO<List<HashtagResponseDTO>> getHashtagsForPost(Integer postId);

    /**
     * Get top N trending hashtags ordered by postCount DESC.
     * Default limit = 10. Used for trending section on homepage.
     */
    ApiResponseDTO<List<HashtagResponseDTO>> getTrendingHashtags(int limit);

    /**
     * Get all postIds tagged with a specific hashtag.
     * Returns enriched PostSearchResultDTO list by fetching post details from post-service.
     */
    ApiResponseDTO<List<PostSearchResultDTO>> getPostsByHashtag(String tag);

    /**
     * Partial hashtag search / autocomplete.
     * Returns hashtags whose tag string contains the query (case-insensitive).
     */
    ApiResponseDTO<List<HashtagResponseDTO>> searchHashtags(String query);

    /**
     * Get the postCount for a specific hashtag.
     * Returns 0 if the hashtag does not exist.
     */
    ApiResponseDTO<Integer> getHashtagCount(String tag);
}
