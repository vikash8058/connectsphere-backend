package com.connectsphere.auth.entity;

/**
 * AuthProvider Enum - How the user authenticated / registered
 *
 * LOCAL   -> Email + password registration
 * GITHUB  -> GitHub OAuth2
 * GOOGLE  -> Google OAuth2
 */
public enum AuthProvider {
    LOCAL,
    GITHUB,
    GOOGLE
}