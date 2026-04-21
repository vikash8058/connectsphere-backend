package com.connectsphere.search.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * HashtagNotFoundException - Thrown when a requested hashtag does not exist
 * Maps to HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class HashtagNotFoundException extends RuntimeException {

    public HashtagNotFoundException(String message) {
        super(message);
    }
}
