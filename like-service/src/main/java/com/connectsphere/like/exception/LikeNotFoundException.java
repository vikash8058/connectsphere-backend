package com.connectsphere.like.exception;

/**
 * Thrown when a like/reaction record is not found.
 * Maps to HTTP 404 Not Found in GlobalExceptionHandler.
 */
public class LikeNotFoundException extends RuntimeException {
    public LikeNotFoundException(String message) {
        super(message);
    }
}