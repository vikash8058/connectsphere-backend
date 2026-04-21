package com.connectsphere.media.exception;

/**
 * StoryNotFoundException - Thrown when a story is not found, has expired, or is inactive.
 *
 * Maps to HTTP 404 in GlobalExceptionHandler.
 * e.g. GET /stories/999/view when storyId=999 does not exist or isActive=false.
 */
public class StoryNotFoundException extends RuntimeException {
    public StoryNotFoundException(String message) {
        super(message);
    }
}
