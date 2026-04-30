package com.connectsphere.follow.entity;


public enum FollowStatus {
    ACTIVE,   // Follow accepted; feeds and FOLLOWERS_ONLY posts visible
    PENDING   // Awaiting followee approval (private account)
}