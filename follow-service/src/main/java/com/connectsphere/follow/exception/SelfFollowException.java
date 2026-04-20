package com.connectsphere.follow.exception;

/**
 * Thrown when a user tries to follow themselves.
 * Maps to HTTP 400 Bad Request in GlobalExceptionHandler.
 */
public class SelfFollowException extends RuntimeException {
    public SelfFollowException(String message) {
        super(message);
    }
}