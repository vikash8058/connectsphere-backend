package com.connectsphere.like.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Like Entity - Maps to 'likes' table in connectsphere_like database
 *
 * Fields as per ConnectSphere case study section 4.4 (Like-Service):
 *   likeId       - Primary key, auto-increment
 *   userId       - Who reacted (references auth-service user, cross-service)
 *   targetId     - ID of the post OR comment being reacted to
 *   targetType   - POST or COMMENT (polymorphic — same table handles both)
 *   reactionType - LIKE / LOVE / HAHA / WOW / SAD / ANGRY
 *   createdAt    - When the reaction was created
 *
 * NFR (section 6):
 * "One-reaction-per-user enforced atomically;
 *  duplicate follow records prevented by unique constraint"
 *
 * The unique constraint on (userId, targetId, targetType) ensures
 * one reaction per user per target at the database level.
 * A user must first REMOVE their existing reaction to change it (changeReaction()).
 */
@Entity
@Table(
    name = "likes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_target",
            columnNames = {"user_id", "target_id", "target_type"}
        )
    },
    indexes = {
        @Index(name = "idx_user_id",          columnList = "user_id"),
        @Index(name = "idx_target_id",        columnList = "target_id"),
        @Index(name = "idx_target_type",      columnList = "target_type"),
        @Index(name = "idx_user_target",      columnList = "user_id, target_id, target_type"),
        @Index(name = "idx_reaction_type",    columnList = "reaction_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Integer likeId;

    /**
     * User who made this reaction.
     * References users.user_id in auth-service (cross-service, no DB FK).
     */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * ID of the entity being reacted to.
     * Is a postId when targetType = POST.
     * Is a commentId when targetType = COMMENT.
     */
    @Column(name = "target_id", nullable = false)
    private Integer targetId;

    /**
     * Type of entity: POST or COMMENT.
     * Together with targetId, uniquely identifies what was liked.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private TargetType targetType;

    /**
     * Reaction type: LIKE, LOVE, HAHA, WOW, SAD, ANGRY.
     * Case study section 2.3: users can change or remove reactions.
     * changeReaction() deletes old + inserts new (atomic via @Transactional).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 10)
    @Builder.Default
    private ReactionType reactionType = ReactionType.LIKE;

    /** Auto-set on INSERT */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}