package com.nzxhjy.agri.common.security;

public final class UserContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_TYPE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String clientType) {
        USER_ID.set(userId);
        CLIENT_TYPE.set(clientType);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getClientType() {
        return CLIENT_TYPE.get();
    }

    public static void clear() {
        USER_ID.remove();
        CLIENT_TYPE.remove();
    }
}
