package com.connectsphere.media.exception;

/**
 * MediaNotFoundException - Thrown when a media item is not found or is soft-deleted.
 *
 * Maps to HTTP 404 in GlobalExceptionHandler.
 * e.g. GET /media/999 when mediaId=999 does not exist or isDeleted=true.
 */
public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(String message) {
        super(message);
    }
}
