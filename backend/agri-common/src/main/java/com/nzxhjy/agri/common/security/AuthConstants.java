package com.nzxhjy.agri.common.security;

public final class AuthConstants {
    public static final String ADMIN_CLIENT = "ADMIN";
    public static final String PORTAL_CLIENT = "PORTAL";
    public static final String TOKEN_KEY_PREFIX = "token:";

    private AuthConstants() {
    }

    public static String tokenKey(Long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }
}
