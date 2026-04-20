package com.connectsphere.follow.dto;

import lombok.*;

/**
 * FollowCountDTO - Follower and following counts for profile display.
 *
 * Case study section 3.2:
 * "View User Profile — See a user's posts, follower/following count, and bio"
 *
 * Both counts returned in a single call to avoid two separate round trips.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowCountDTO {

    private Integer userId;
    private Integer followerCount;    // How many users follow this person
    private Integer followingCount;   // How many users this person follows
}