package com.connectsphere.like.exception;

/**
 * Thrown when a user tries to react to a target they already reacted to.
 * Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 */
public class AlreadyLikedException extends RuntimeException {
    public AlreadyLikedException(String message) {
        super(message);
    }
}