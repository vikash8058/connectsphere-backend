package com.connectsphere.post.messaging;

import com.connectsphere.post.dto.IndexPostEventMessage;
import com.connectsphere.post.entity.Post;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PostEventPublisher postEventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postEventPublisher, "postExchange", "test.exchange");
        ReflectionTestUtils.setField(postEventPublisher, "postCreatedRoutingKey", "created.key");
        ReflectionTestUtils.setField(postEventPublisher, "postUpdatedRoutingKey", "updated.key");
        ReflectionTestUtils.setField(postEventPublisher, "postDeletedRoutingKey", "deleted.key");
    }

    @Test
    void publishPostCreated_success() {
        Post post = Post.builder().postId(1).authorId(10).content("Content").build();
        postEventPublisher.publishPostCreated(post);
        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("created.key"), any(IndexPostEventMessage.class));
    }

    @Test
    void publishPostUpdated_success() {
        Post oldPost = Post.builder().postId(1).content("Old").build();
        Post newPost = Post.builder().postId(1).content("New").authorId(10).build();
        postEventPublisher.publishPostUpdated(oldPost, newPost);
        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("updated.key"), any(IndexPostEventMessage.class));
    }

    @Test
    void publishPostDeleted_success() {
        postEventPublisher.publishPostDeleted(1, 10);
        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("deleted.key"), any(IndexPostEventMessage.class));
    }
    
    @Test
    void publishPostCreated_handlesException() {
        doThrow(new RuntimeException("Rabbit down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(IndexPostEventMessage.class));
        
        Post post = Post.builder().postId(1).build();
        // Should not throw exception, just log it
        assertDoesNotThrow(() -> postEventPublisher.publishPostCreated(post));
    }
}
