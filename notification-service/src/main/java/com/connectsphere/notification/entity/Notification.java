package com.connectsphere.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_recipient_id",          columnList = "recipient_id"),
        @Index(name = "idx_recipient_is_read",     columnList = "recipient_id, is_read"),
        @Index(name = "idx_actor_target",          columnList = "actor_id, target_id"),
        @Index(name = "idx_type",                  columnList = "type"),
        @Index(name = "idx_created_at",            columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Integer notificationId;

    /**
     * User who should receive this notification.
     * References users.user_id in auth-service. Not a DB foreign key.
     */
    @Column(name = "recipient_id", nullable = false)
    private Integer recipientId;

    /**
     * User who triggered the event.
     * E.g. actorId = 5 → "User 5 liked your post"
     * References users.user_id in auth-service. Not a DB foreign key.
     */
    @Column(name = "actor_id", nullable = false)
    private Integer actorId;

    /**
     * Notification event type: LIKE, COMMENT, REPLY, FOLLOW, MENTION
     * Stored as a VARCHAR string in DB for readability.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    /**
     * Human-readable notification text.
     * E.g. "vikash liked your post", "rahul commented on your post"
     */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * ID of the entity this notification is about.
     * For LIKE/COMMENT/REPLY/MENTION: postId or commentId
     * For FOLLOW: followeeId (the person who was followed)
     * Cross-service reference — not a DB foreign key.
     */
    @Column(name = "target_id")
    private Integer targetId;

    /**
     * Describes what targetId refers to: "POST" or "COMMENT"
     * Null for FOLLOW type (no specific content target).
     */
    @Column(name = "target_type", length = 20)
    private String targetType;

    /**
     * Frontend deep-link URL — where to navigate when user clicks this notification.
     * E.g. "/posts/42" for a post like, "/profile/7" for a new follower
     */
    @Column(name = "deep_link_url", length = 500)
    private String deepLinkUrl;

    /**
     * Read/unread state.
     * false = unread (counted in badge)
     * true  = read (excluded from badge count)
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    /** Auto-set on INSERT — notifications are never updated, only read or deleted */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}