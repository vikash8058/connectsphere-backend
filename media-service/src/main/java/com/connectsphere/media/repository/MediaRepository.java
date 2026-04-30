package com.connectsphere.media.repository;

import com.connectsphere.media.entity.Media;
import com.connectsphere.media.entity.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface MediaRepository extends JpaRepository<Media, Integer> {

    /**
     * Find all active (non-deleted) media uploaded by a specific user.
     * Used for "My Uploads" page and profile media gallery.
     */
    List<Media> findByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(Integer uploaderId);

    /**
     * Find a single media item by ID — only if not soft-deleted.
     */
    Optional<Media> findByMediaIdAndIsDeletedFalse(Integer mediaId);

    /**
     * Find all active media linked to a specific post.
     * Used when rendering post detail to show attached images/videos.
     */
    List<Media> findByLinkedPostIdAndIsDeletedFalse(Integer linkedPostId);

    /**
     * Find all active media of a specific type (IMAGE or VIDEO) by a user.
     * Useful for media gallery filtering.
     */
    List<Media> findByUploaderIdAndMediaTypeAndIsDeletedFalse(
            Integer uploaderId, MediaType mediaType);

    /**
     * Soft delete a single media item by ID.
     * Sets isDeleted = true — record is retained for 30-day audit trail (NFR).
     */
    @Modifying
    @Query("UPDATE Media m SET m.isDeleted = true WHERE m.mediaId = :mediaId")
    void softDeleteByMediaId(@Param("mediaId") Integer mediaId);

    /**
     * Soft delete ALL media linked to a deleted post.
     * Called when post-service performs a soft delete on a post.
     * "Media attached to deleted posts is also soft-deleted but retained for audit" (case study 2.6)
     */
    @Modifying
    @Query("UPDATE Media m SET m.isDeleted = true WHERE m.linkedPostId = :postId")
    void softDeleteByLinkedPostId(@Param("postId") Integer postId);

    /**
     * Link a previously uploaded media item to a post.
     * Called after a post is created with pre-uploaded CDN media URLs.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Media m SET m.linkedPostId = :postId WHERE m.mediaId = :mediaId AND m.isDeleted = false")
    void linkMediaToPost(@Param("mediaId") Integer mediaId, @Param("postId") Integer postId);

    /**
     * Check if a media item belongs to a specific uploader (ownership check).
     */
    boolean existsByMediaIdAndUploaderId(Integer mediaId, Integer uploaderId);

    /**
     * Count total media uploaded by a user (non-deleted).
     */
    int countByUploaderIdAndIsDeletedFalse(Integer uploaderId);
}
