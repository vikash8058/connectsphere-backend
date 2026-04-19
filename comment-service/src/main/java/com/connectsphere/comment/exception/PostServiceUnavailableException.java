package com.connectsphere.comment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when post-service is down and comment-service
 * cannot verify post existence.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PostServiceUnavailableException extends RuntimeException {

    public PostServiceUnavailableException(String message) {

        super(message);
    }
}