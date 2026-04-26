package com.connectsphere.media.entity;

/**
 * Visibility Enum - Controls who can see a story
 *
 * PUBLIC         -> Visible to everyone
 * FOLLOWERS_ONLY -> Visible only to followers of the author
 * PRIVATE        -> Visible only to the author themselves
 */
public enum Visibility {
    PUBLIC,
    FOLLOWERS_ONLY,
    PRIVATE
}
