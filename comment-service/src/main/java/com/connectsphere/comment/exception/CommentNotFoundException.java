package com.connectsphere.comment.exception;

/**
 * CommentNotFoundException - Thrown when a comment does not exist
 */
public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(String message) {
        super(message);
    }
}