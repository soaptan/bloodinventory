package com.fyp.bloodinventory.config;

import java.util.regex.Pattern;

public final class PasswordHashSupport {

    private static final String BCRYPT_PREFIX = "{bcrypt}";
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("\\A\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}\\z");

    private PasswordHashSupport() {
    }

    public static String normalizeStoredPassword(String password) {
        if (password == null) {
            return null;
        }

        String normalized = password.trim();
        if (normalized.startsWith(BCRYPT_PREFIX)) {
            normalized = normalized.substring(BCRYPT_PREFIX.length());
        }

        return normalized;
    }

    public static boolean isBcryptHash(String password) {
        String normalized = normalizeStoredPassword(password);
        return normalized != null && BCRYPT_PATTERN.matcher(normalized).matches();
    }
}
