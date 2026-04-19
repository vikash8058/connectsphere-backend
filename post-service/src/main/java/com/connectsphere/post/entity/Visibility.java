package com.connectsphere.post.entity;

/**
 * Visibility Enum - Controls who can see a post
 *
 * PUBLIC         -> Visible to everyone including guests (unauthenticated)
 * FOLLOWERS_ONLY -> Visible only to approved followers of the author
 * PRIVATE        -> Visible only to the author themselves
 *
 * Case study section 2.3: "Set post visibility: Public, Followers-only, or Private"
 */
public enum Visibility {
    PUBLIC,
    FOLLOWERS_ONLY,
    PRIVATE
}