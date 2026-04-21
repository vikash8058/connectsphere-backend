package com.connectsphere.media.exception;

/**
 * UnauthorizedActionException - Thrown when a user attempts to modify/delete
 * a media item or story they do not own (and are not an admin).
 *
 * Maps to HTTP 403 in GlobalExceptionHandler.
 * e.g. User A tries to delete User B's media upload (non-admin).
 */
public class UnauthorizedActionException extends RuntimeException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}
