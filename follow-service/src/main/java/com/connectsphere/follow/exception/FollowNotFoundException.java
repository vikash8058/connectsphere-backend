package com.connectsphere.follow.exception;

/**
 * Thrown when a follow relationship is not found.
 * Maps to HTTP 404 Not Found in GlobalExceptionHandler.
 */
public class FollowNotFoundException extends RuntimeException {
    public FollowNotFoundException(String message) {
        super(message);
    }
}