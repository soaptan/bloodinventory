package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.config.PasswordHashSupport;
import com.fyp.bloodinventory.config.PasswordPolicy;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PasswordResetService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseAuditContextService auditContextService;
    private final SystemNotificationService notificationService;

    public PasswordResetService(JdbcTemplate jdbcTemplate,
                                PasswordEncoder passwordEncoder,
                                DatabaseAuditContextService auditContextService,
                                SystemNotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditContextService = auditContextService;
        this.notificationService = notificationService;
    }

    public Optional<Long> verifyIdentity(String username, String email, String icNumber) {
        String normalizedUsername = requireText(username, "Please enter your username.");
        String normalizedEmail = requireText(email, "Please enter your registered email.");
        String normalizedIcNumber = requireText(icNumber, "Please enter your IC number.");

        return Optional.ofNullable(findActiveAccountByIdentity(
                normalizedUsername,
                normalizedEmail,
                normalizedIcNumber
        )).map(StaffAccount::staffId);
    }

    @Transactional
    public void resetPasswordForVerifiedStaff(Long staffId,
                                              String newPassword,
                                              String confirmPassword,
                                              String sourceIp) {
        String normalizedNewPassword = PasswordPolicy.requireStrongPassword(newPassword);
        String normalizedConfirmPassword = requireConfirmation(confirmPassword);
        if (!normalizedNewPassword.equals(normalizedConfirmPassword)) {
            throw new RuntimeException("New password and confirmation do not match.");
        }

        StaffAccount account = findActiveAccountById(staffId);
        if (account == null) {
            throw new RuntimeException("Verified account is no longer available.");
        }

        completeDirectReset(account, normalizedNewPassword, sourceIp);
    }

    private void completeDirectReset(StaffAccount account,
                                     String normalizedNewPassword,
                                     String sourceIp) {
        auditContextService.applyCurrentContext();

        String storedPassword = PasswordHashSupport.normalizeStoredPassword(account.password());
        if (PasswordHashSupport.isBcryptHash(storedPassword)
                && passwordEncoder.matches(normalizedNewPassword, storedPassword)) {
            throw new RuntimeException("New password must be different from the current password.");
        }

        jdbcTemplate.update("""
                UPDATE staff
                SET password = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    last_modified_by = ?
                WHERE staff_id = ?
                """, passwordEncoder.encode(normalizedNewPassword), account.username(), account.staffId());
        jdbcTemplate.update("""
                UPDATE staff_password_reset_token
                SET used_at = CURRENT_TIMESTAMP
                WHERE staff_id = ?
                  AND used_at IS NULL
                """, account.staffId());
        jdbcTemplate.update("""
                UPDATE staff_login_session
                SET status = 'ENDED',
                    ended_at = CURRENT_TIMESTAMP,
                    end_reason = 'PASSWORD_RESET'
                WHERE LOWER(username) = LOWER(?)
                  AND status = 'ACTIVE'
                """, account.username());

        notificationService.record(
                "Password Reset",
                "RESET_COMPLETE",
                "Password reset completed for " + account.username(),
                account.username(),
                sourceIp
        );
    }

    private StaffAccount findActiveAccountByIdentity(String username, String email, String icNumber) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT staff_id, username, password
                    FROM staff
                    WHERE LOWER(username) = LOWER(?)
                      AND LOWER(email) = LOWER(?)
                      AND ic_number = ?
                      AND is_active = TRUE
                    """, (rs, rowNum) -> new StaffAccount(
                    rs.getLong("staff_id"),
                    rs.getString("username"),
                    rs.getString("password")
            ), username, email, icNumber);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private StaffAccount findActiveAccountById(Long staffId) {
        if (staffId == null || staffId <= 0) {
            return null;
        }

        try {
            return jdbcTemplate.queryForObject("""
                    SELECT staff_id, username, password
                    FROM staff
                    WHERE staff_id = ?
                      AND is_active = TRUE
                    """, (rs, rowNum) -> new StaffAccount(
                    rs.getLong("staff_id"),
                    rs.getString("username"),
                    rs.getString("password")
            ), staffId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private String requireConfirmation(String value) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException("Please confirm the new password.");
        }
        return value;
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record StaffAccount(Long staffId, String username, String password) {
    }
}
