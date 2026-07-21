package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.PasswordResetRequestResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private static final String GENERIC_REQUEST_MESSAGE =
            "If an active account matches those details, a verification code has been sent to the registered email.";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseAuditContextService auditContextService;
    private final SystemNotificationService notificationService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    private final int codeMinutes;
    private final String mailHost;
    private final String mailFrom;
    private final String mailUsername;
    private final String mailPassword;

    public PasswordResetService(JdbcTemplate jdbcTemplate,
                                PasswordEncoder passwordEncoder,
                                DatabaseAuditContextService auditContextService,
                                SystemNotificationService notificationService,
                                ObjectProvider<JavaMailSender> mailSenderProvider,
                                @Value("${app.password-reset.code-minutes:${app.password-reset.token-minutes:10}}") int codeMinutes,
                                @Value("${spring.mail.host:}") String mailHost,
                                @Value("${spring.mail.from:}") String mailFrom,
                                @Value("${spring.mail.username:}") String mailUsername,
                                @Value("${spring.mail.password:}") String mailPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditContextService = auditContextService;
        this.notificationService = notificationService;
        this.mailSenderProvider = mailSenderProvider;
        this.codeMinutes = Math.max(5, codeMinutes);
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
    }

    @Transactional
    public PasswordResetRequestResult requestResetForIdentity(String username,
                                                              String email,
                                                              String icNumber,
                                                              String sourceIp) {
        String normalizedUsername = requireText(username, "Please enter your username.");
        String normalizedEmail = requireText(email, "Please enter your registered email.");
        String normalizedIcNumber = requireText(icNumber, "Please enter your IC number.");

        StaffAccount account = findActiveAccountByIdentity(normalizedUsername, normalizedEmail, normalizedIcNumber);
        if (account == null) {
            return new PasswordResetRequestResult(GENERIC_REQUEST_MESSAGE, false, null);
        }

        return createVerificationCode(account, sourceIp);
    }

    @Transactional
    public void resetPasswordWithCode(String username,
                                      String email,
                                      String icNumber,
                                      String verificationCode,
                                      String newPassword,
                                      String confirmPassword) {
        String normalizedUsername = requireText(username, "Please enter your username.");
        String normalizedEmail = requireText(email, "Please enter your registered email.");
        String normalizedIcNumber = requireText(icNumber, "Please enter your IC number.");
        StaffAccount account = findActiveAccountByIdentity(normalizedUsername, normalizedEmail, normalizedIcNumber);
        if (account == null) {
            throw new RuntimeException("Verification code is invalid or expired.");
        }

        completeResetWithCode(account, verificationCode, newPassword, confirmPassword);
    }

    private PasswordResetRequestResult createVerificationCode(StaffAccount account, String sourceIp) {
        JavaMailSender mailSender = requireMailSender();
        auditContextService.applyCurrentContext();

        jdbcTemplate.update("""
                UPDATE staff_password_reset_token
                SET used_at = CURRENT_TIMESTAMP
                WHERE staff_id = ?
                  AND used_at IS NULL
                """, account.staffId());

        String code = generateVerificationCode();
        jdbcTemplate.update("""
                INSERT INTO staff_password_reset_token (staff_id, token_hash, expires_at, request_ip)
                VALUES (?, ?, ?, ?)
                """,
                account.staffId(),
                verificationCodeHash(account.staffId(), code),
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(codeMinutes)),
                truncate(sourceIp, 80));

        try {
            sendVerificationCodeEmail(mailSender, account, code);
        } catch (MailAuthenticationException ex) {
            jdbcTemplate.update("""
                    UPDATE staff_password_reset_token
                    SET used_at = CURRENT_TIMESTAMP
                    WHERE staff_id = ?
                      AND used_at IS NULL
                    """, account.staffId());
            throw new RuntimeException("Gmail rejected the SMTP login. Use the sender Gmail address in MAIL_USERNAME and a Gmail app password in MAIL_PASSWORD, then restart the app.");
        } catch (MailSendException ex) {
            jdbcTemplate.update("""
                    UPDATE staff_password_reset_token
                    SET used_at = CURRENT_TIMESTAMP
                    WHERE staff_id = ?
                      AND used_at IS NULL
                    """, account.staffId());
            throw new RuntimeException("Verification email could not be sent because the SMTP server was unreachable or refused the message. Check host, port, TLS, and network access.");
        } catch (MailException ex) {
            jdbcTemplate.update("""
                    UPDATE staff_password_reset_token
                    SET used_at = CURRENT_TIMESTAMP
                    WHERE staff_id = ?
                      AND used_at IS NULL
                    """, account.staffId());
            throw new RuntimeException("Verification email could not be sent. Check the SMTP sender settings and try again.");
        }

        notificationService.record(
                "Password Reset",
                "RESET_REQUEST",
                "Password reset verification code sent for " + account.username(),
                account.username(),
                sourceIp
        );

        return new PasswordResetRequestResult(
                "A verification code has been sent to " + maskEmail(account.email()) + ".",
                true,
                maskEmail(account.email())
        );
    }

    private void completeResetWithCode(StaffAccount account,
                                       String verificationCode,
                                       String newPassword,
                                       String confirmPassword) {
        String normalizedCode = requireVerificationCode(verificationCode);
        String normalizedNewPassword = requirePassword(newPassword);
        String normalizedConfirmPassword = requirePassword(confirmPassword);
        if (!normalizedNewPassword.equals(normalizedConfirmPassword)) {
            throw new RuntimeException("New password and confirmation do not match.");
        }

        auditContextService.applyCurrentContext();
        ResetToken resetToken = findUsableCode(account.staffId(), verificationCodeHash(account.staffId(), normalizedCode));
        if (resetToken == null) {
            throw new RuntimeException("Verification code is invalid or expired.");
        }

        String encodedPassword = passwordEncoder.encode(normalizedNewPassword);
        jdbcTemplate.update("""
                UPDATE staff
                SET password = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    last_modified_by = ?
                WHERE staff_id = ?
                """, encodedPassword, account.username(), account.staffId());
        jdbcTemplate.update("""
                UPDATE staff_password_reset_token
                SET used_at = CURRENT_TIMESTAMP
                WHERE token_id = ?
                  AND used_at IS NULL
                """, resetToken.tokenId());

        notificationService.record(
                "Password Reset",
                "RESET_COMPLETE",
                "Password reset completed for " + account.username(),
                account.username()
        );
    }

    private StaffAccount findActiveAccountByIdentity(String username, String email, String icNumber) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT staff_id, username, email, full_name
                    FROM staff
                    WHERE LOWER(username) = LOWER(?)
                      AND LOWER(email) = LOWER(?)
                      AND ic_number = ?
                      AND is_active = TRUE
                    """, (rs, rowNum) -> new StaffAccount(
                    rs.getLong("staff_id"),
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("full_name")
            ), username, email, icNumber);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private ResetToken findUsableCode(Long staffId, String codeHash) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT t.token_id
                    FROM staff_password_reset_token t
                    JOIN staff s ON s.staff_id = t.staff_id
                    WHERE t.staff_id = ?
                      AND t.token_hash = ?
                      AND t.used_at IS NULL
                      AND t.expires_at > CURRENT_TIMESTAMP
                      AND s.is_active = TRUE
                    FOR UPDATE
                    """, (rs, rowNum) -> new ResetToken(rs.getLong("token_id")), staffId, codeHash);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private JavaMailSender requireMailSender() {
        if (trimToNull(mailHost) == null) {
            throw new RuntimeException("Email delivery is not configured. Set spring.mail.host and SMTP credentials first.");
        }
        if (mailHost.toLowerCase().contains("gmail.com")
                && (trimToNull(mailUsername) == null || trimToNull(mailPassword) == null)) {
            throw new RuntimeException("Gmail SMTP needs MAIL_USERNAME and a Gmail app password in MAIL_PASSWORD. The profile email is only the recipient address.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new RuntimeException("Email delivery is not available. Check the mail dependency and SMTP settings.");
        }

        return mailSender;
    }

    private void sendVerificationCodeEmail(JavaMailSender mailSender, StaffAccount account, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(account.email());
        String fromAddress = trimToNull(mailFrom);
        if (fromAddress == null) {
            fromAddress = trimToNull(mailUsername);
        }
        if (fromAddress != null) {
            message.setFrom(fromAddress);
        }
        message.setSubject("Blood Inventory password reset code");
        message.setText("""
                Hello %s,

                Your Blood Inventory Management System password reset code is:

                %s

                This code expires in %d minutes. If you did not request this reset, ignore this email.
                """.formatted(displayName(account), code, codeMinutes));
        mailSender.send(message);
    }

    private String generateVerificationCode() {
        return String.valueOf(100000 + secureRandom.nextInt(900000));
    }

    private String verificationCodeHash(Long staffId, String code) {
        return sha256Hex(staffId + ":" + code);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private String displayName(StaffAccount account) {
        String fullName = trimToNull(account.fullName());
        return fullName == null ? account.username() : fullName;
    }

    private String maskEmail(String email) {
        String normalized = trimToNull(email);
        if (normalized == null || !normalized.contains("@")) {
            return "the registered email";
        }

        String[] parts = normalized.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        String visibleLocal = local.length() <= 2
                ? local.substring(0, 1)
                : local.substring(0, 2);
        return visibleLocal + "***@" + domain;
    }

    private String requireVerificationCode(String value) {
        String normalized = requireText(value, "Please enter the verification code.");
        if (!normalized.matches("\\d{6}")) {
            throw new RuntimeException("Verification code must be 6 digits.");
        }
        return normalized;
    }

    private String requirePassword(String value) {
        String normalized = requireText(value, "Please enter the new password.");
        if (normalized.length() < 8) {
            throw new RuntimeException("New password must contain at least 8 characters.");
        }
        return normalized;
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

    private String truncate(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private record StaffAccount(Long staffId, String username, String email, String fullName) {
    }

    private record ResetToken(Long tokenId) {
    }
}
