package com.connectsphere.media.scheduler;

import com.connectsphere.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * StoryExpiryScheduler - Scheduled Job for Story Expiry
 *
 * Purpose (case study section 2.6 + NFR section 6):
 *   "Stories expire exactly 24 hours after creation; a scheduled job purges expired stories."
 *   NFR: "Stories are purged within 5 minutes of their 24-hour expiry via a scheduled cleanup job"
 *
 * This scheduler runs every 5 minutes (cron: "0 *\/5 * * * *").
 * It calls MediaService.expireOldStories() which executes a single batch UPDATE:
 *   UPDATE stories SET is_active = false WHERE expires_at <= NOW() AND is_active = true
 *
 * @EnableScheduling must be present on the main application class (MediaServiceApplication).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoryExpiryScheduler {

    private final MediaService mediaService;

    /**
     * Runs every 5 minutes to deactivate stories that have passed their expiresAt timestamp.
     *
     * Cron expression: "0 *\/5 * * * *"
     *   - Second:  0   (fire at the start of a minute)
     *   - Minute:  *\/5 (every 5 minutes)
     *   - Hour:    *   (every hour)
     *   - Day:     *   (every day)
     *   - Month:   *   (every month)
     *   - Weekday: *   (every weekday)
     *
     * NFR: "Stories are purged within 5 minutes of their 24-hour expiry"
     */
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
