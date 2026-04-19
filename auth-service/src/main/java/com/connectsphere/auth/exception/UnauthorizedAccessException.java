package com.connectsphere.auth.exception;

/**
 * UnauthorizedAccessException - Thrown when a user tries to perform
 * an action not permitted for their role.
 *
 * Examples:
 *   - USER tries to assign roles
 *   - MODERATOR tries to permanently delete a user
 *   - Admin tries to deactivate their own account
 *
 * Maps to HTTP 403 Forbidden in GlobalExceptionHandler.
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }
}