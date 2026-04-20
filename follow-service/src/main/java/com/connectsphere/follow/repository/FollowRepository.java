package com.connectsphere.follow.repository;

import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FollowRepository - Data Access Layer for Follow Entity
 *
 * Custom queries as per ConnectSphere case study section 4.5:
 *   findByFollowerIdAndFolloweeId()          - Check specific follow relationship
 *   findByFollowerId()                        - All users this person follows
 *   findByFolloweeId()                        - All followers of a person
 *   existsByFollowerIdAndFolloweeId()         - Boolean check for isFollowing()
 *   countByFollowerId()                       - Following count
 *   countByFolloweeId()                       - Follower count
 *   findMutualFollows()                       - Mutual connections
 *   deleteByFollowerIdAndFolloweeId()         - Unfollow
 */
@Repository
public interface FollowRepository extends JpaRepository<Follow, Integer> {

    /**
     * Find a specific follow relationship between two users.
     * Used by isFollowing() and unfollow().
     */
    Optional<Follow> findByFollowerIdAndFolloweeId(Integer followerId, Integer followeeId);

    /**
     * Get all ACTIVE follow records where this user is the follower.
     * Returns the list of users this person is following.
     * Used by getFollowing() and getFeedForUser() (followeeIds).
     */
    List<Follow> findByFollowerIdAndStatus(Integer followerId, FollowStatus status);

    /**
     * Get all ACTIVE follow records where this user is the followee.
     * Returns the list of users following this person.
     * Used by getFollowers().
     */
    List<Follow> findByFolloweeIdAndStatus(Integer followeeId, FollowStatus status);

    /**
     * Check if a follow relationship exists (any status).
     * Used by isFollowing() for a quick boolean check.
     */
    boolean existsByFollowerIdAndFolloweeId(Integer followerId, Integer followeeId);

    /**
     * Check if an ACTIVE follow relationship exists.
     * Used to verify followee access to FOLLOWERS_ONLY posts.
     */
    boolean existsByFollowerIdAndFolloweeIdAndStatus(
            Integer followerId, Integer followeeId, FollowStatus status);

    /**
     * Count how many users this person is actively following.
     * Used for the "Following" badge count on the profile page.
     */
    int countByFollowerIdAndStatus(Integer followerId, FollowStatus status);

    /**
     * Count how many users are actively following this person.
     * Used for the "Followers" badge count on the profile page.
     */
    int countByFolloweeIdAndStatus(Integer followeeId, FollowStatus status);

    /**
     * Find mutual follows — users that both A follows B AND B follows A.
     * Used for:
     *  - "Mutual connections" display on profiles
     *  - Moderation trust scoring
     *  - Suggested users algorithm (second-degree connections)
     *
     * Logic: find followeeIds where A follows B AND B follows A back.
     */
    @Query("""
            SELECT f1.followeeId FROM Follow f1
            WHERE f1.followerId = :userId
            AND f1.status = 'ACTIVE'
            AND EXISTS (
                SELECT 1 FROM Follow f2
                WHERE f2.followerId = f1.followeeId
                AND f2.followeeId = :userId
                AND f2.status = 'ACTIVE'
            )
            """)
    List<Integer> findMutualFollows(@Param("userId") Integer userId);

    /**
     * Suggested users to follow — second-degree connections.
     *
     * Logic: users followed by people the current user follows,
     *        but NOT already followed by the current user,
     *        and NOT the current user themselves.
     *
     * Case study section 2.3:
     * "Discover suggested users to follow based on mutual connections."
     */
    @Query("""
            SELECT DISTINCT f2.followeeId FROM Follow f1
            JOIN Follow f2 ON f1.followeeId = f2.followerId
            WHERE f1.followerId = :userId
            AND f1.status = 'ACTIVE'
            AND f2.status = 'ACTIVE'
            AND f2.followeeId <> :userId
            AND f2.followeeId NOT IN (
                SELECT f3.followeeId FROM Follow f3
                WHERE f3.followerId = :userId
            )
            """)
    List<Integer> findSuggestedUsers(@Param("userId") Integer userId);

    /**
     * Get IDs of all users this person is actively following.
     * Used by post-service to build the news feed.
     * Returns only followeeIds (not full Follow entities) for efficiency.
     */
    @Query("SELECT f.followeeId FROM Follow f WHERE f.followerId = :followerId AND f.status = 'ACTIVE'")
    List<Integer> findFolloweeIdsByFollowerId(@Param("followerId") Integer followerId);

    /**
     * Unfollow — delete the follow record between two users.
     */
    @Modifying
    @Query("DELETE FROM Follow f WHERE f.followerId = :followerId AND f.followeeId = :followeeId")
    void deleteByFollowerIdAndFolloweeId(
            @Param("followerId") Integer followerId,
            @Param("followeeId") Integer followeeId);
}