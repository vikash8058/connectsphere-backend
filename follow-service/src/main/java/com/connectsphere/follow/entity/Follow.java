package com.connectsphere.follow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Follow Entity - Maps to 'follows' table in connectsphere_follow database
 *
 * Fields as per ConnectSphere case study section 4.5 (Follow-Service):
 *   followId   - Primary key, auto-increment
 *   followerId - User who is following (the actor)
 *   followeeId - User being followed (the target)
 *   status     - ACTIVE or PENDING (for private accounts)
 *   createdAt  - When the follow relationship was created
 *
 * NFR (section 6):
 * "Duplicate follow records prevented by unique constraint"
 *
 * The unique constraint on (followerId, followeeId) ensures a user
 * cannot follow the same person twice at the DB level.
 *
 * Cross-service references:
 *   followerId → users.user_id in auth-service (no DB FK)
 *   followeeId → users.user_id in auth-service (no DB FK)
 */
@Entity
@Table(
    name = "follows",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_follower_followee",
            columnNames = {"follower_id", "followee_id"}
        )
    },
    indexes = {
        @Index(name = "idx_follower_id",  columnList = "follower_id"),
        @Index(name = "idx_followee_id",  columnList = "followee_id"),
        @Index(name = "idx_status",       columnList = "status"),
        @Index(name = "idx_created_at",   columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_id")
    private Integer followId;

    /**
     * User who initiated the follow action.
     * References users.user_id in auth-service (cross-service, no DB FK).
     */
    @Column(name = "follower_id", nullable = false)
    private Integer followerId;

    /**
     * User being followed.
     * References users.user_id in auth-service (cross-service, no DB FK).
     */
    @Column(name = "followee_id", nullable = false)
    private Integer followeeId;

    /**
     * State of this follow relationship.
     * ACTIVE  = normal public account — follow is immediately active
     * PENDING = private account — follow awaits followee approval
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private FollowStatus status = FollowStatus.ACTIVE;

    /** Auto-set on INSERT */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}