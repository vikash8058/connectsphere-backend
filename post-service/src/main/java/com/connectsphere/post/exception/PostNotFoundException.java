package com.connectsphere.post.exception;

/**
 * PostNotFoundException - Thrown when a post is not found or is soft-deleted
 */
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(String message) {
        super(message);
    }
}