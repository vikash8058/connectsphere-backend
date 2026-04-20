package com.connectsphere.notification.entity;

/**
 * NotificationType - All social event types that trigger notifications
 *
 * From case study section 4.6:
 *   LIKE     - A user liked your post or comment
 *   COMMENT  - A user commented on your post
 *   REPLY    - A user replied to your comment
 *   FOLLOW   - A user followed you
 *   MENTION  - A user mentioned you in a post or comment
 */
public enum NotificationType {
    LIKE,
    COMMENT,
    REPLY,
    FOLLOW,
    MENTION
}