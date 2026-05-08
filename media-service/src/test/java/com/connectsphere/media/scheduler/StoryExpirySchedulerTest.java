package com.connectsphere.media.scheduler;

import com.connectsphere.media.service.MediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryExpirySchedulerTest {

    @Mock private MediaService mediaService;
    @InjectMocks private StoryExpiryScheduler scheduler;

    @Test
    void expireOldStories_withExpired() {
        when(mediaService.expireOldStories()).thenReturn(5);
        assertDoesNotThrow(() -> scheduler.expireOldStories());
        verify(mediaService).expireOldStories();
    }

    @Test
    void expireOldStories_noneExpired() {
        when(mediaService.expireOldStories()).thenReturn(0);
        assertDoesNotThrow(() -> scheduler.expireOldStories());
    }

    @Test
    void expireOldStories_exception() {
        when(mediaService.expireOldStories()).thenThrow(new RuntimeException("DB fail"));
        assertDoesNotThrow(() -> scheduler.expireOldStories());
    }
}
