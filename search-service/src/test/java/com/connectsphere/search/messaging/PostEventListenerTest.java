package com.connectsphere.search.messaging;

import com.connectsphere.search.dto.IndexPostEventMessage;
import com.connectsphere.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostEventListenerTest {

    @Mock
    private SearchService searchService;

    @InjectMocks
    private PostEventListener postEventListener;

    @Test
    void handlePostCreated() {
        IndexPostEventMessage message = new IndexPostEventMessage();
        message.setPostId(1);
        message.setAuthorId(2);
        message.setContent("Hello #java");
        message.setVisibility("PUBLIC");

        postEventListener.handlePostCreated(message);

        verify(searchService).indexPost(1, 2, "Hello #java", "PUBLIC");
    }

    @Test
    void handlePostCreated_error() {
        IndexPostEventMessage message = new IndexPostEventMessage();
        doThrow(new RuntimeException("Fail")).when(searchService).indexPost(any(), any(), any(), any());

        // Should catch exception and log it (no exception thrown out)
        postEventListener.handlePostCreated(message);

        verify(searchService).indexPost(any(), any(), any(), any());
    }

    @Test
    void handlePostUpdated() {
        IndexPostEventMessage message = new IndexPostEventMessage();
        message.setPostId(1);
        message.setAuthorId(2);
        message.setContent("New content #spring");
        message.setPreviousContent("Old content #java");
        message.setVisibility("PUBLIC");

        postEventListener.handlePostUpdated(message);

        verify(searchService).reIndexPost(1, 2, "New content #spring", "Old content #java", "PUBLIC");
    }

    @Test
    void handlePostUpdated_error() {
        IndexPostEventMessage message = new IndexPostEventMessage();
        doThrow(new RuntimeException("Fail")).when(searchService).reIndexPost(any(), any(), any(), any(), any());

        postEventListener.handlePostUpdated(message);

        verify(searchService).reIndexPost(any(), any(), any(), any(), any());
    }

    @Test
    void handlePostDeleted() {
        IndexPostEventMessage message = new IndexPostEventMessage();
        message.setPostId(1);

        postEventListener.handlePostDeleted(message);

        verify(searchService).removePostIndex(1);
    }

    @Test
    void handlePostDeleted_error() {
        IndexPostEventMessage message = new IndexPostEventMessage();
        doThrow(new RuntimeException("Fail")).when(searchService).removePostIndex(any());

        postEventListener.handlePostDeleted(message);

        verify(searchService).removePostIndex(any());
    }
}
