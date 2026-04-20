package com.connectsphere.like.entity;

/**
 * ReactionType - Six Facebook-style reaction types.
 *
 * Case study section 4.4:
 * "It supports six reaction types (Like, Love, Haha, Wow, Sad, Angry)"
 *
 * Case study section 2.3:
 * "Like or react to posts (Like, Love, Haha, Wow, Sad, Angry); change or remove reactions."
 */
public enum ReactionType {
    LIKE,
    LOVE,
    HAHA,
    WOW,
    SAD,
    ANGRY
}