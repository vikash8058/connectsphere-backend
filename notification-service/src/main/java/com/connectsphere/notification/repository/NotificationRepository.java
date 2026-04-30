package com.connectsphere.notification.repository;

import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    /**
     * Fetch all notifications for a recipient, newest first.
     * Used for rendering the notification dropdown/feed.
     */
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Integer recipientId);

    /**
     * Fetch notifications filtered by read/unread state for a recipient.
     * isRead=false → unread only, isRead=true → read only
     */
    List<Notification> findByRecipientIdAndIsReadOrderByCreatedAtDesc(Integer recipientId,
                                                                      Boolean isRead);

    /**
     * Count unread notifications for a recipient.
     * Used for the red badge number in the top nav bar.
     */
    int countByRecipientIdAndIsRead(Integer recipientId, Boolean isRead);

    /**
     * Fetch all notifications of a specific type (e.g. all FOLLOW notifications).
     * Useful for admin analytics and moderation.
     */
    List<Notification> findByTypeOrderByCreatedAtDesc(NotificationType type);

    /**
     * Find notifications by actor and target (deduplication check).
     * Prevents sending duplicate "User X liked your post" notifications.
     * e.g. findByActorIdAndTargetId(5, 42) → has user 5 already liked post 42?
     */
    Optional<Notification> findByActorIdAndTargetIdAndType(Integer actorId,
                                                           Integer targetId,
                                                           NotificationType type);

    /**
     * Find a notification by its ID for ownership verification before delete.
     */
    Optional<Notification> findByNotificationId(Integer notificationId);

    /**
     * Delete a notification by its primary key.
     * Used when a user deletes a specific notification.
     */
    void deleteByNotificationId(Integer notificationId);

    /**
     * Mark a single notification as read atomically.
     * Avoids loading the full entity just to flip the isRead flag.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.notificationId = :notificationId")
    void markAsReadById(@Param("notificationId") Integer notificationId);

    /**
     * Mark ALL notifications for a recipient as read in a single UPDATE.
     * Called when user clicks "Mark all as read".
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :recipientId AND n.isRead = false")
    void markAllAsReadByRecipient(@Param("recipientId") Integer recipientId);

    /**
     * Check if a notification exists for a given recipient.
     * Quick existence check before performing operations.
     */
    boolean existsByNotificationIdAndRecipientId(Integer notificationId, Integer recipientId);

    /**
     * Count how many FOLLOW notifications a user has received.
     * Used by isFollowerMilestone() to decide if an email should be sent.
     * e.g. count = 100 → send "You reached 100 followers!" email.
     */
    long countByRecipientIdAndType(Integer recipientId, NotificationType type);
}