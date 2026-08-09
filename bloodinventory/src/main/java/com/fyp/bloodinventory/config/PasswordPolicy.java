package com.fyp.bloodinventory.config;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    private static final Pattern LOWERCASE_CHARACTER = Pattern.compile("[a-z]");
    private static final Pattern UPPERCASE_CHARACTER = Pattern.compile("[A-Z]");
    private static final Pattern NUMBER_CHARACTER = Pattern.compile("[0-9]");
    private static final Pattern SYMBOL_CHARACTER = Pattern.compile("[^A-Za-z0-9\\s]");

    private PasswordPolicy() {
    }

    public static String requireStrongPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new RuntimeException("Please enter a new password.");
        }

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new RuntimeException("Password must be between 8 and 72 characters.");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
            throw new RuntimeException("Password must not exceed 72 bytes.");
        }

        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new RuntimeException("Password cannot contain spaces.");
        }

        if (!LOWERCASE_CHARACTER.matcher(password).find()
                || !UPPERCASE_CHARACTER.matcher(password).find()
                || !NUMBER_CHARACTER.matcher(password).find()
                || !SYMBOL_CHARACTER.matcher(password).find()) {
            throw new RuntimeException(
                    "Password must include uppercase and lowercase letters, a number, and a special character."
            );
        }

        return password;
    }
}
