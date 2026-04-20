package com.connectsphere.follow.exception;

/**
 * Thrown when a user tries to follow someone they already follow.
 * Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 */
public class AlreadyFollowingException extends RuntimeException {
    public AlreadyFollowingException(String message) {
        super(message);
    }
}