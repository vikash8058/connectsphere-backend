package com.connectsphere.media.exception;

/**
 * UnsupportedMediaTypeException - Thrown when an uploaded file has an unsupported MIME type.
 *
 * Maps to HTTP 415 (Unsupported Media Type) in GlobalExceptionHandler.
 *
 * Allowed MIME types (case study section 2.6):
 *   Images: image/jpeg, image/png, image/webp
 *   Videos: video/mp4
 */
public class UnsupportedMediaTypeException extends RuntimeException {
    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
