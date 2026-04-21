package com.connectsphere.search.controller;

import com.connectsphere.search.dto.*;
import com.connectsphere.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SearchResource - REST Controller for ConnectSphere Search/Hashtag Service
 *
 * Base path: /api/v1 (via context-path in application.yml)
 *
 * Endpoints split into two groups:
 *   /search/**   → post search and user search
 *   /hashtags/** → hashtag feed, trending, autocomplete, counts
 *
 * Public endpoints (no JWT required — guests can search):
 *   GET /search/posts?keyword=
 *   GET /search/users?query=
 *   GET /hashtags/trending
 *   GET /hashtags/{tag}/posts
 *   GET /hashtags/search?query=
 *   GET /hashtags/{tag}/count
 *   GET /hashtags/post/{postId}
 *
 * All public per case study section 2.2 — guests can search users and hashtags.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Search & Hashtag Service", description = "Hashtag indexing, trending, and full-text search")
public class SearchResource {

    private final SearchService searchService;

    // ── SEARCH ENDPOINTS ──────────────────────────────────────────────────────

    /**
     * Full-text keyword search across posts.
     * Supports plain keywords ("spring boot") and hashtags ("#java").
     * Public — guests can search.
     */
    @GetMapping("/search/posts")
    @Operation(summary = "Search posts by keyword or hashtag",
               description = "Keyword search across post content. Prefix with # for hashtag search.")
    public ResponseEntity<ApiResponseDTO<List<PostSearchResultDTO>>> searchPosts(
            @RequestParam String keyword) {

        log.debug("GET /search/posts keyword='{}'", keyword);
        return ResponseEntity.ok(searchService.searchPosts(keyword));
    }

    /**
     * Search users by username or fullName.
     * Delegates to auth-service via Feign.
     * Public — guests can search users (case study section 2.2).
     */
    @GetMapping("/search/users")
    @Operation(summary = "Search users by username or full name",
               description = "Case-insensitive search across username and fullName fields.")
    public ResponseEntity<ApiResponseDTO<List<UserSearchResultDTO>>> searchUsers(
            @RequestParam String query) {

        log.debug("GET /search/users query='{}'", query);
        return ResponseEntity.ok(searchService.searchUsers(query));
    }

    // ── HASHTAG ENDPOINTS ─────────────────────────────────────────────────────

    /**
     * Get top N trending hashtags ordered by postCount DESC.
     * Default limit = 10, max = 50.
     * Public — used on homepage trending section.
     */
    @GetMapping("/hashtags/trending")
    @Operation(summary = "Get trending hashtags",
               description = "Returns top N hashtags ordered by post count. Default limit=10.")
    public ResponseEntity<ApiResponseDTO<List<HashtagResponseDTO>>> getTrendingHashtags(
            @RequestParam(defaultValue = "10") int limit) {

        log.debug("GET /hashtags/trending limit={}", limit);
        return ResponseEntity.ok(searchService.getTrendingHashtags(limit));
    }

    /**
     * Get all posts tagged with a specific hashtag.
     * e.g. GET /hashtags/springboot/posts
     * Public — hashtag feeds are public.
     */
    @GetMapping("/hashtags/{tag}/posts")
    @Operation(summary = "Get all posts for a hashtag",
               description = "Returns all PUBLIC posts tagged with #tag, newest first.")
    public ResponseEntity<ApiResponseDTO<List<PostSearchResultDTO>>> getPostsByHashtag(
            @PathVariable String tag) {

        log.debug("GET /hashtags/{}/posts", tag);
        return ResponseEntity.ok(searchService.getPostsByHashtag(tag));
    }

    /**
     * Get all hashtags on a specific post.
     * e.g. GET /hashtags/post/42
     */
    @GetMapping("/hashtags/post/{postId}")
    @Operation(summary = "Get hashtags for a specific post",
               description = "Returns all hashtags attached to the given postId.")
    public ResponseEntity<ApiResponseDTO<List<HashtagResponseDTO>>> getHashtagsForPost(
            @PathVariable Integer postId) {

        log.debug("GET /hashtags/post/{}", postId);
        return ResponseEntity.ok(searchService.getHashtagsForPost(postId));
    }

    /**
     * Hashtag autocomplete — partial search.
     * e.g. GET /hashtags/search?query=spring → ["spring", "springboot", "springframework"]
     * Used while typing a hashtag in the post creation input.
     */
    @GetMapping("/hashtags/search")
    @Operation(summary = "Autocomplete hashtag search",
               description = "Returns hashtags whose tag contains the query string. Ordered by postCount.")
    public ResponseEntity<ApiResponseDTO<List<HashtagResponseDTO>>> searchHashtags(
            @RequestParam String query) {

        log.debug("GET /hashtags/search query='{}'", query);
        return ResponseEntity.ok(searchService.searchHashtags(query));
    }

    /**
     * Get the postCount for a specific hashtag.
     * e.g. GET /hashtags/java/count → 1532
     */
    @GetMapping("/hashtags/{tag}/count")
    @Operation(summary = "Get post count for a hashtag",
               description = "Returns total number of posts using this hashtag. Returns 0 if not found.")
    public ResponseEntity<ApiResponseDTO<Integer>> getHashtagCount(
            @PathVariable String tag) {

        log.debug("GET /hashtags/{}/count", tag);
        return ResponseEntity.ok(searchService.getHashtagCount(tag));
    }
}
