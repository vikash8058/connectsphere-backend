package com.connectsphere.follow.entity;

/**
 * FollowStatus - State of a follow relationship.
 *
 * Case study section 4.5:
 * "followId, followerId, followeeId, status (ACTIVE/PENDING for private accounts), createdAt"
 *
 * ACTIVE  → follow is accepted and the follower can see the followee's posts
 * PENDING → followee has a private account; awaiting manual approval
 *           (private account support — case study section 2.3:
 *            "Set post visibility: Public, Followers-only, or Private")
 */
public enum FollowStatus {
    ACTIVE,   // Follow accepted; feeds and FOLLOWERS_ONLY posts visible
    PENDING   // Awaiting followee approval (private account)
}