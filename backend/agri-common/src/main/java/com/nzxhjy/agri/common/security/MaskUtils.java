package com.nzxhjy.agri.common.security;

public final class MaskUtils {
    private MaskUtils() {
    }

    public static String phone(String value) {
        return mask(value, 3, 4);
    }

    public static String idCard(String value) {
        return mask(value, 6, 4);
    }

    public static String bankCard(String value) {
        return mask(value, 4, 4);
    }

    private static String mask(String value, int prefixLength, int suffixLength) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= prefixLength + suffixLength) {
            return "*".repeat(value.length());
        }
        return value.substring(0, prefixLength)
                + "*".repeat(value.length() - prefixLength - suffixLength)
                + value.substring(value.length() - suffixLength);
    }
}
