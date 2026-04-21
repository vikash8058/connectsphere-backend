package com.connectsphere.post.messaging;

import com.connectsphere.post.entity.Post;
import com.connectsphere.post.dto.IndexPostEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PostEventPublisher - Publishes post lifecycle events to RabbitMQ
 *
 * Sends events whenever a post is:
 *   - Created  → indexPost() in search-service
 *   - Updated  → reIndexPost() in search-service
 *   - Deleted  → removePostIndex() in search-service
 *
 * This integrates post-service with search-service via async messaging.
 * search-service consumes from the same exchange/queues this publisher sends to.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${post.rabbitmq.exchange:connectsphere.post.exchange}")
    private String postExchange;

    @Value("${post.rabbitmq.post-created-routing-key:post.created}")
    private String postCreatedRoutingKey;

    @Value("${post.rabbitmq.post-updated-routing-key:post.updated}")
    private String postUpdatedRoutingKey;

    @Value("${post.rabbitmq.post-deleted-routing-key:post.deleted}")
    private String postDeletedRoutingKey;

    /**
     * Publish POST_CREATED event.
     * Called when a new post is created.
     */
    public void publishPostCreated(Post post) {
        try {
            IndexPostEventMessage event = IndexPostEventMessage.builder()
                    .eventType("POST_CREATED")
                    .postId(post.getPostId())
                    .authorId(post.getAuthorId())
                    .content(post.getContent())
                    .visibility(post.getVisibility() != null 
                            ? post.getVisibility().toString() 
                            : "PUBLIC")
                    .build();

            rabbitTemplate.convertAndSend(postExchange, postCreatedRoutingKey, event);
            log.info("Published POST_CREATED event: postId={}, authorId={}", 
                    post.getPostId(), post.getAuthorId());
        } catch (Exception e) {
            log.error("Failed to publish POST_CREATED event for postId={}: {}", 
                    post.getPostId(), e.getMessage(), e);
        }
    }

    /**
     * Publish POST_UPDATED event.
     * Called when a post is updated.
     */
    public void publishPostUpdated(Post oldPost, Post updatedPost) {
        try {
            IndexPostEventMessage event = IndexPostEventMessage.builder()
                    .eventType("POST_UPDATED")
                    .postId(updatedPost.getPostId())
                    .authorId(updatedPost.getAuthorId())
                    .content(updatedPost.getContent())
                    .previousContent(oldPost.getContent())
                    .visibility(updatedPost.getVisibility() != null 
                            ? updatedPost.getVisibility().toString() 
                            : "PUBLIC")
                    .build();

            rabbitTemplate.convertAndSend(postExchange, postUpdatedRoutingKey, event);
            log.info("Published POST_UPDATED event: postId={}", updatedPost.getPostId());
        } catch (Exception e) {
            log.error("Failed to publish POST_UPDATED event for postId={}: {}", 
                    updatedPost.getPostId(), e.getMessage(), e);
        }
    }

    /**
     * Publish POST_DELETED event.
     * Called when a post is deleted.
     */
    public void publishPostDeleted(Integer postId, Integer authorId) {
        try {
            IndexPostEventMessage event = IndexPostEventMessage.builder()
                    .eventType("POST_DELETED")
                    .postId(postId)
                    .authorId(authorId)
                    .build();

            rabbitTemplate.convertAndSend(postExchange, postDeletedRoutingKey, event);
            log.info("Published POST_DELETED event: postId={}", postId);
        } catch (Exception e) {
            log.error("Failed to publish POST_DELETED event for postId={}: {}", 
                    postId, e.getMessage(), e);
        }
    }
}

