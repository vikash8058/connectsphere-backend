package com.connectsphere.like.entity;

/**
 * TargetType - The kind of entity being liked/reacted to.
 *
 * Case study section 4.4:
 * "The Like-Service provides a polymorphic reaction system that can
 *  target any entity — posts or comments — via the targetId and targetType fields."
 */
public enum TargetType {
    POST,     // Reaction is on a post
    COMMENT   // Reaction is on a comment
}