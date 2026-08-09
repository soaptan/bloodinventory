package com.fyp.bloodinventory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class LoginAttemptService {

    private static final int IP_LIMIT_MULTIPLIER = 5;

    private final JdbcTemplate jdbcTemplate;
    private final int maxAttempts;
    private final int windowMinutes;
    private final int blockMinutes;

    public LoginAttemptService(JdbcTemplate jdbcTemplate,
                               @Value("${app.security.login.max-attempts:5}") int maxAttempts,
                               @Value("${app.security.login.window-minutes:15}") int windowMinutes,
                               @Value("${app.security.login.block-minutes:15}") int blockMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxAttempts = clamp(maxAttempts, 3, 20);
        this.windowMinutes = clamp(windowMinutes, 1, 60);
        this.blockMinutes = clamp(blockMinutes, 1, 120);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String username, String sourceAddress) {
        Boolean blocked = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM authentication_throttle
                    WHERE attempt_key IN (?, ?, ?)
                      AND blocked_until > CURRENT_TIMESTAMP
                )
                """, Boolean.class,
                accountKey(username),
                accountAddressKey(username, sourceAddress),
                addressKey(sourceAddress));
        return Boolean.TRUE.equals(blocked);
    }

    @Transactional
    public boolean recordFailure(String username, String sourceAddress) {
        LocalDateTime now = LocalDateTime.now();
        removeExpiredEntries(now);
        boolean accountBlocked = incrementBucket(accountKey(username), maxAttempts, now);
        boolean accountAddressBlocked = incrementBucket(accountAddressKey(username, sourceAddress), maxAttempts, now);
        boolean addressBlocked = incrementBucket(
                addressKey(sourceAddress),
                Math.max(maxAttempts * IP_LIMIT_MULTIPLIER, 20),
                now
        );
        return accountBlocked || accountAddressBlocked || addressBlocked;
    }

    @Transactional
    public boolean recordRejectedInput(String sourceAddress) {
        LocalDateTime now = LocalDateTime.now();
        removeExpiredEntries(now);
        return incrementBucket(
                addressKey(sourceAddress),
                Math.max(maxAttempts * IP_LIMIT_MULTIPLIER, 20),
                now
        );
    }

    @Transactional
    public void recordSuccess(String username, String sourceAddress) {
        jdbcTemplate.update("""
                DELETE FROM authentication_throttle
                WHERE attempt_key IN (?, ?)
                """, accountKey(username), accountAddressKey(username, sourceAddress));
    }

    public int retryAfterSeconds() {
        return blockMinutes * 60;
    }

    private boolean incrementBucket(String attemptKey, int attemptLimit, LocalDateTime now) {
        Timestamp nowTimestamp = Timestamp.valueOf(now);
        jdbcTemplate.update("""
                INSERT INTO authentication_throttle (
                    attempt_key,
                    failure_count,
                    window_started_at,
                    last_failed_at,
                    blocked_until
                )
                VALUES (?, 0, ?, ?, NULL)
                ON CONFLICT (attempt_key) DO NOTHING
                """, attemptKey, nowTimestamp, nowTimestamp);

        AttemptBucket bucket = jdbcTemplate.queryForObject("""
                SELECT failure_count, window_started_at, blocked_until
                FROM authentication_throttle
                WHERE attempt_key = ?
                FOR UPDATE
                """, (rs, rowNum) -> new AttemptBucket(
                rs.getInt("failure_count"),
                rs.getTimestamp("window_started_at").toLocalDateTime(),
                rs.getTimestamp("blocked_until") == null
                        ? null
                        : rs.getTimestamp("blocked_until").toLocalDateTime()
        ), attemptKey);

        if (bucket == null) {
            throw new IllegalStateException("Unable to initialize login attempt protection.");
        }

        if (bucket.blockedUntil() != null && bucket.blockedUntil().isAfter(now)) {
            return true;
        }

        boolean windowExpired = !bucket.windowStartedAt().plusMinutes(windowMinutes).isAfter(now)
                || (bucket.blockedUntil() != null && !bucket.blockedUntil().isAfter(now));
        int failureCount = windowExpired ? 1 : bucket.failureCount() + 1;
        LocalDateTime windowStartedAt = windowExpired ? now : bucket.windowStartedAt();
        LocalDateTime blockedUntil = failureCount >= attemptLimit ? now.plusMinutes(blockMinutes) : null;

        jdbcTemplate.update("""
                UPDATE authentication_throttle
                SET failure_count = ?,
                    window_started_at = ?,
                    last_failed_at = ?,
                    blocked_until = ?
                WHERE attempt_key = ?
                """,
                failureCount,
                Timestamp.valueOf(windowStartedAt),
                nowTimestamp,
                blockedUntil == null ? null : Timestamp.valueOf(blockedUntil),
                attemptKey);

        return blockedUntil != null;
    }

    private void removeExpiredEntries(LocalDateTime now) {
        jdbcTemplate.update("""
                DELETE FROM authentication_throttle
                WHERE last_failed_at < ?
                  AND (blocked_until IS NULL OR blocked_until < ?)
                """, Timestamp.valueOf(now.minusDays(1)), Timestamp.valueOf(now));
    }

    private String accountKey(String username) {
        return digest("ACCOUNT:" + normalizeUsername(username));
    }

    private String accountAddressKey(String username, String sourceAddress) {
        return digest("ACCOUNT_ADDRESS:" + normalizeUsername(username) + ":" + normalizeAddress(sourceAddress));
    }

    private String addressKey(String sourceAddress) {
        return digest("ADDRESS:" + normalizeAddress(sourceAddress));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAddress(String sourceAddress) {
        if (sourceAddress == null || sourceAddress.isBlank()) {
            return "unknown";
        }
        String normalized = sourceAddress.trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record AttemptBucket(int failureCount, LocalDateTime windowStartedAt, LocalDateTime blockedUntil) {
    }
}
