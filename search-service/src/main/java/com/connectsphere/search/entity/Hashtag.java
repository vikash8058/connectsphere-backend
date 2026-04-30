package com.connectsphere.search.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "hashtags",
    indexes = {
        @Index(name = "idx_tag",          columnList = "tag"),
        @Index(name = "idx_post_count",   columnList = "post_count DESC"),
        @Index(name = "idx_last_used_at", columnList = "last_used_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Hashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hashtag_id")
    private Integer hashtagId;

    /**
     * The hashtag text — stored lowercase, no # prefix.
     * e.g. post content "#SpringBoot" → stored as "springboot"
     * Unique constraint enforces one record per tag.
     */
    @Column(name = "tag", nullable = false, unique = true, length = 100)
    private String tag;

    /**
     * Denormalised post count — incremented when a post with this tag is indexed,
     * decremented when a post with this tag is deleted.
     * Drives trending hashtag ranking — ORDER BY post_count DESC.
     */
    @Column(name = "post_count", nullable = false)
    @Builder.Default
    private Integer postCount = 0;

    /**
     * Auto-updated every time this hashtag is used in a new post.
     * Used for time-windowed trending queries (e.g. trending in last 24h).
     */
    @UpdateTimestamp
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
