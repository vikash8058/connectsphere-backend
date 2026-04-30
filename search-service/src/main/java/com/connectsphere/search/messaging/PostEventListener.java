package com.connectsphere.search.messaging;

import com.connectsphere.search.dto.IndexPostEventMessage;
import com.connectsphere.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventListener {

    private final SearchService searchService;

    /**
     * Handles POST_CREATED events.
     * Indexes hashtags from the new post content.
     *
     * Queue: connectsphere.search.post.created.queue
     */
    @RabbitListener(queues = "${search.rabbitmq.post-created-queue}")
    public void handlePostCreated(IndexPostEventMessage event) {
        log.info("PostEventListener → POST_CREATED: postId={} authorId={}",
                event.getPostId(), event.getAuthorId());
        try {
            searchService.indexPost(
                    event.getPostId(),
                    event.getAuthorId(),
                    event.getContent(),
                    event.getVisibility()
            );
            log.info("PostEventListener → POST_CREATED indexed: postId={}", event.getPostId());
        } catch (Exception e) {
            log.error("PostEventListener → POST_CREATED failed for postId={}: {}",
                    event.getPostId(), e.getMessage(), e);
        }
    }

    /**
     * Handles POST_UPDATED events.
     * Re-indexes hashtags: removes stale tags, adds new ones.
     *
     * Queue: connectsphere.search.post.updated.queue
     */
    @RabbitListener(queues = "${search.rabbitmq.post-updated-queue}")
    public void handlePostUpdated(IndexPostEventMessage event) {
        log.info("PostEventListener → POST_UPDATED: postId={}", event.getPostId());
        try {
            searchService.reIndexPost(
                    event.getPostId(),
                    event.getAuthorId(),
                    event.getContent(),
                    event.getPreviousContent(),
                    event.getVisibility()
            );
            log.info("PostEventListener → POST_UPDATED re-indexed: postId={}", event.getPostId());
        } catch (Exception e) {
            log.error("PostEventListener → POST_UPDATED failed for postId={}: {}",
                    event.getPostId(), e.getMessage(), e);
        }
    }

    /**
     * Handles POST_DELETED events.
     * Removes all PostHashtag mappings and decrements hashtag postCounts.
     *
     * Queue: connectsphere.search.post.deleted.queue
     */
    @RabbitListener(queues = "${search.rabbitmq.post-deleted-queue}")
    public void handlePostDeleted(IndexPostEventMessage event) {
        log.info("PostEventListener → POST_DELETED: postId={}", event.getPostId());
        try {
            searchService.removePostIndex(event.getPostId());
            log.info("PostEventListener → POST_DELETED index removed: postId={}", event.getPostId());
        } catch (Exception e) {
            log.error("PostEventListener → POST_DELETED failed for postId={}: {}",
                    event.getPostId(), e.getMessage(), e);
        }
    }
}
