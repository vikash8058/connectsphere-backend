package com.connectsphere.search.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(
    name = "post_hashtags",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_post_hashtag",
            columnNames = {"post_id", "hashtag_id"}
        )
    },
    indexes = {
        @Index(name = "idx_post_id",    columnList = "post_id"),
        @Index(name = "idx_hashtag_id", columnList = "hashtag_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PostHashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    /**
     * ID of the post that uses this hashtag.
     * References posts.post_id in post-service (cross-service, no DB FK).
     */
    @Column(name = "post_id", nullable = false)
    private Integer postId;

    /**
     * The hashtag that this post uses.
     * ManyToOne — many PostHashtag rows can point to the same Hashtag.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hashtag_id", nullable = false)
    private Hashtag hashtag;

    /** Auto-set on INSERT */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
