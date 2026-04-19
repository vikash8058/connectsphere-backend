package com.connectsphere.auth.entity;

/**
 * Role Enum - Defines all user roles in ConnectSphere
 *
 * USER      -> Registered member (creates posts, likes, comments, follows)
 * ADMIN     -> Platform administrator (full access, moderation, analytics)
 * MODERATOR -> Content moderator (reviews flagged content, resolves reports)
 */
public enum Role {
    USER,
    ADMIN,
    MODERATOR
}