package com.connectsphere.search.dto;

import lombok.*;

import java.util.List;

/**
 * IndexPostEventMessage - Message payload for post indexing events sent by post-service.
 * Contains all info needed to index or remove a post from the search index.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
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
