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
 *   SYSTEM   - A system generated notification
 *   PAYMENT_SUCCESS - Razorpay payment successful
 *   SUBSCRIPTION_EXPIRY - Elite subscription expired
 */
public enum NotificationType {
    LIKE,
    COMMENT,
    REPLY,
    FOLLOW,
    MENTION,
    SYSTEM,
    PAYMENT_SUCCESS,
    SUBSCRIPTION_EXPIRY
}