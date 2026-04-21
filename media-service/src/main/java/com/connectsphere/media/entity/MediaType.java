package com.connectsphere.media.entity;

/**
 * MediaType Enum - Type of uploaded media
 *
 * IMAGE -> JPEG, PNG, WebP image files (as per case study section 2.6)
 * VIDEO -> MP4 video files (as per case study section 2.6)
 *
 * Case study section 2.6: "Users can upload images (JPEG, PNG, WebP)
 * and short videos (MP4) up to configurable size limits."
 */
public enum MediaType {
    IMAGE,
    VIDEO
}
