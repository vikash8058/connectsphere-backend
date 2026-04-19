package com.connectsphere.post.exception;

/**
 * UnauthorizedActionException - Thrown when a user tries to edit/delete
 * a post they do not own.
 *
 * e.g. User A tries to delete User B's post (non-admin).
 */
public class UnauthorizedActionException extends RuntimeException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}