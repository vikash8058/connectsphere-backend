package com.connectsphere.comment.exception;

/**
 * UnauthorizedActionException - Thrown when user tries to modify
 * a comment they do not own (and are not an admin/moderator).
 */
public class UnauthorizedActionException extends RuntimeException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}