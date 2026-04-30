package com.connectsphere.media.scheduler;

import com.connectsphere.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class StoryExpiryScheduler {

    private final MediaService mediaService;

    @Scheduled(cron = "${story.expiry-check-cron}")
    public void expireOldStories() {
        log.info("StoryExpiryScheduler triggered — checking for expired stories...");

        try {
            int expired = mediaService.expireOldStories();
            if (expired > 0) {
                log.info("StoryExpiryScheduler: Deactivated {} expired story/stories.", expired);
            } else {
                log.debug("StoryExpiryScheduler: No expired stories found.");
            }
        } catch (Exception e) {
            // Scheduler failures are logged but must not crash the service
            log.error("StoryExpiryScheduler: Error during story expiry — {}", e.getMessage(), e);
        }
    }
}
