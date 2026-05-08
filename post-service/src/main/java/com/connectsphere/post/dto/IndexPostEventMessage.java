package com.connectsphere.post.dto;

import lombok.*;

/**
 * IndexPostEventMessage - RabbitMQ message published by post-service
 *
 * post-service publishes this message whenever a post is:
 *   - Created  → search-service indexes hashtags, adds PostHashtag mappings
 *   - Updated  → search-service re-indexes: removes old tags, adds new tags
 *   - Deleted  → search-service removes all PostHashtag mappings, decrements counts
 *
 * eventType values: "POST_CREATED", "POST_UPDATED", "POST_DELETED"
 *
 * content field is the full post text — search-service parses #hashtags from it.
 * For POST_DELETED, content can be null (postId is sufficient).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class IndexPostEventMessage {

    /** Event type: POST_CREATED / POST_UPDATED / POST_DELETED */
    private String eventType;

    /** ID of the post being indexed/removed */
    private Integer postId;

    /** ID of the post author — for user-based search enrichment */
    private Integer authorId;

    /**
     * Full post text content — search-service parses #tag tokens from this.
     * Null for POST_DELETED events.
     */
    private String content;

    /** Visibility of the post — only PUBLIC posts are indexed for search */
    private String visibility;

    /** Previous content (for POST_UPDATED) — needed to diff old vs new tags */
    private String previousContent;
}

