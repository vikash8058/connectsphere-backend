package com.connectsphere.post.entity;

/**
 * PostType Enum - Type of post content
 *
 * TEXT  -> Text-only post (no media attached)
 * IMAGE -> Post with one or more image URLs
 * VIDEO -> Post with a video URL
 *
 * Case study section 2.3: "Create posts — text-only or with attached media (images, videos)"
 */
public enum PostType {
    TEXT,
    IMAGE,
    VIDEO
}