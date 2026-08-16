package com.fyp.bloodinventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowTests {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    AuthenticationFlowTests(MockMvc mockMvc,
                            JdbcTemplate jdbcTemplate,
                            PasswordEncoder passwordEncoder) {
        this.mockMvc = Objects.requireNonNull(mockMvc, "MockMvc must not be null.");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null.");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder must not be null.");
    }

    @Test
    void manualLogoutAllowsCleanSubsequentLogin() throws Exception {
        cleanupAuthenticationTestAccounts();
        String username = "auth.test." + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        createTestAdministrator(username);
        try {
            MvcResult firstLogin = login(username)
                    .andExpect(expect(status().is3xxRedirection()))
                    .andExpect(expect(redirectedUrl("/admin/dashboard")))
                    .andReturn();
            MockHttpSession session = (MockHttpSession) firstLogin.getRequest().getSession(false);

            mockMvc.perform(post("/logout")
                            .with(csrf())
                            .session(session))
                    .andExpect(expect(status().is3xxRedirection()))
                    .andExpect(expect(redirectedUrl("/login?logout")));

            login(username)
                    .andExpect(expect(status().is3xxRedirection()))
                    .andExpect(expect(redirectedUrl("/admin/dashboard")));
        } finally {
            jdbcTemplate.update("DELETE FROM staff_login_session WHERE username = ?", username);
            jdbcTemplate.update("DELETE FROM staff WHERE username = ?", username);
        }
    }

    @Test
    void failedLoginWritesAuditEvent() throws Exception {
        long before = countAuditAction("LOGIN_FAILURE");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "wrong-password"))
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/login?error")));

        assertThat(countAuditAction("LOGIN_FAILURE")).isGreaterThan(before);
        Map<String, Object> latestFailure = latestAuditAction("LOGIN_FAILURE");
        assertThat(latestFailure.get("event_category")).isEqualTo("SECURITY");
        assertThat(latestFailure.get("operation_type")).isEqualTo("LOGIN");
        assertThat(latestFailure.get("workflow_phase")).isEqualTo("Authentication");
        assertThat(latestFailure.get("integrity_hash")).asString().hasSize(64);
        assertThat(latestFailure.get("process_context")).asString().contains("attempted_username");
    }

    @Test
    void blankLoginUsesGenericCredentialErrorInsteadOfAuthenticating() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", " ")
                        .param("password", ""))
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/login?error")));
    }

    @Test
    void loginRejectsSqlInjectionShapedUsername() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "admin' OR '1'='1")
                        .param("password", "anything"))
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/login?error")));
    }

    @Test
    void repeatedLoginFailuresAreThrottled() throws Exception {
        String username = "missing." + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            for (int attempt = 1; attempt < 5; attempt++) {
                mockMvc.perform(post("/login")
                                .with(csrf())
                                .param("username", username)
                                .param("password", "WrongPassword1!"))
                        .andExpect(expect(status().is3xxRedirection()))
                        .andExpect(expect(redirectedUrl("/login?error")));
            }

            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "WrongPassword1!"))
                    .andExpect(expect(status().is3xxRedirection()))
                    .andExpect(expect(redirectedUrl("/login?throttled")))
                    .andExpect(expect(header().string("Retry-After", "900")));

            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "WrongPassword1!"))
                    .andExpect(expect(status().is3xxRedirection()))
                    .andExpect(expect(redirectedUrl("/login?throttled")));
        } finally {
            jdbcTemplate.update("DELETE FROM authentication_throttle");
        }
    }

    @Test
    void loginPageIncludesBrowserSecurityHeaders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(header().string("X-Content-Type-Options", "nosniff")))
                .andExpect(expect(header().string("X-Frame-Options", "DENY")))
                .andExpect(expect(header().string("Referrer-Policy", "same-origin")))
                .andExpect(expect(header().string("Cross-Origin-Opener-Policy", "same-origin")))
                .andExpect(expect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("object-src 'none'"))));
    }

    @Test
    void recoveryFormsRejectMalformedIdentityBeforeDatabaseLookup() throws Exception {
        mockMvc.perform(post("/forgot-username")
                        .with(csrf())
                        .param("email", "not-an-email")
                        .param("icNumber", "123"))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(org.hamcrest.Matchers.containsString("Enter a valid email address"))))
                .andExpect(expect(content().string(org.hamcrest.Matchers.containsString("Enter a 12-digit IC number"))));

        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("username", "bad username")
                        .param("email", "not-an-email")
                        .param("icNumber", "123"))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(org.hamcrest.Matchers.containsString("Use 3-50 letters"))));
    }

    @Test
    void forgotPasswordPageOnlyCollectsIdentityDetails() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("Verify Account"))))
                .andExpect(expect(content().string(containsString("name=\"username\""))))
                .andExpect(expect(content().string(containsString("name=\"email\""))))
                .andExpect(expect(content().string(containsString("name=\"icNumber\""))))
                .andExpect(expect(content().string(not(containsString("name=\"newPassword\"")))))
                .andExpect(expect(content().string(not(containsString("name=\"confirmPassword\"")))))
                .andExpect(expect(content().string(not(containsString("verificationCode")))))
                .andExpect(expect(content().string(not(containsString("Send Verification Code")))));
    }

    @Test
    void resetPasswordPageRequiresFreshVerifiedSession() throws Exception {
        mockMvc.perform(get("/reset-password"))
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/forgot-password?verificationRequired")));

        MockHttpSession expiredSession = new MockHttpSession();
        expiredSession.setAttribute("passwordResetStaffId", 1L);
        expiredSession.setAttribute("passwordResetVerifiedAt", System.currentTimeMillis() - 660_000L);

        mockMvc.perform(get("/reset-password").session(expiredSession))
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/forgot-password?verificationRequired")));

        assertThat(expiredSession.getAttribute("passwordResetStaffId")).isNull();
        assertThat(expiredSession.getAttribute("passwordResetVerifiedAt")).isNull();
    }

    @Test
    void matchingIdentityImmediatelyOpensSeparatePasswordPage() throws Exception {
        cleanupPasswordResetTestAccounts();
        String username = "reset.test." + UUID.randomUUID();
        String email = username + "@bloodbank.my";
        String icNumber = "990101-10-9001";
        Long staffId = createPasswordResetTestAccount(
                username, email, icNumber, passwordEncoder.encode("OldPassword123!"));

        try {
            MockHttpSession resetSession = verifyPasswordResetIdentity(username, email, icNumber);

            assertThat(resetSession.getAttribute("passwordResetStaffId")).isEqualTo(staffId);
            assertThat(resetSession.getAttribute("passwordResetVerifiedAt")).isInstanceOf(Long.class);

            mockMvc.perform(get("/reset-password").session(resetSession))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(content().string(containsString("Choose New Password"))))
                    .andExpect(expect(content().string(containsString("name=\"newPassword\""))))
                    .andExpect(expect(content().string(containsString("name=\"confirmPassword\""))))
                    .andExpect(expect(content().string(containsString("New password requirements"))))
                    .andExpect(expect(content().string(containsString("maximum 72 bytes"))))
                    .andExpect(expect(content().string(not(containsString("name=\"username\"")))))
                    .andExpect(expect(content().string(not(containsString("name=\"email\"")))))
                    .andExpect(expect(content().string(not(containsString("name=\"icNumber\"")))));
        } finally {
            cleanupPasswordResetTestAccount(username);
        }
    }

    @Test
    void passwordResetRejectsWeakAndMismatchedPasswords() throws Exception {
        cleanupPasswordResetTestAccounts();
        String username = "reset.test." + UUID.randomUUID();
        String email = username + "@bloodbank.my";
        String icNumber = "990101-10-9002";
        String storedPassword = passwordEncoder.encode("OldPassword123!");
        createPasswordResetTestAccount(username, email, icNumber, storedPassword);

        try {
            MockHttpSession resetSession = verifyPasswordResetIdentity(username, email, icNumber);

            mockMvc.perform(post("/reset-password")
                            .session(resetSession)
                            .with(csrf())
                            .param("newPassword", "weak")
                            .param("confirmPassword", "DifferentPassword123!"))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(content().string(containsString("uppercase and lowercase"))))
                    .andExpect(expect(content().string(containsString("Passwords do not match"))));

            String passwordAfterRequest = jdbcTemplate.queryForObject(
                    "SELECT password FROM staff WHERE username = ?",
                    String.class,
                    username
            );
            assertThat(passwordAfterRequest).isEqualTo(storedPassword);

            mockMvc.perform(get("/reset-password").session(resetSession))
                    .andExpect(expect(status().isOk()));
        } finally {
            cleanupPasswordResetTestAccount(username);
        }
    }

    @Test
    void verifiedPasswordResetUpdatesAccountAndConsumesApproval() throws Exception {
        cleanupPasswordResetTestAccounts();
        String username = "reset.test." + UUID.randomUUID();
        String email = username + "@bloodbank.my";
        String icNumber = "990101-10-9003";
        Long staffId = createPasswordResetTestAccount(
                username, email, icNumber, passwordEncoder.encode("OldPassword123!"));
        String sessionId = "reset-session-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO staff_login_session (
                    session_id, username, source_ip, status, created_at, last_seen_at, expires_at
                )
                VALUES (?, ?, '127.0.0.1', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, sessionId, username);
        jdbcTemplate.update("""
                INSERT INTO staff_password_reset_token (
                    staff_id, token_hash, requested_at, expires_at, request_ip
                )
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour', '127.0.0.1')
                """, staffId, "legacy-" + UUID.randomUUID());

        try {
            MockHttpSession resetSession = verifyPasswordResetIdentity(username, email, icNumber);

            mockMvc.perform(post("/reset-password")
                            .session(resetSession)
                            .with(csrf())
                            .param("newPassword", "NewPassword123!")
                            .param("confirmPassword", "NewPassword123!"))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(content().string(containsString("Password reset successfully"))));

            Integer unusedTokens = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM staff_password_reset_token t
                    JOIN staff s ON s.staff_id = t.staff_id
                    WHERE s.username = ?
                      AND t.used_at IS NULL
                    """, Integer.class, username);
            assertThat(unusedTokens).isZero();

            Map<String, Object> session = jdbcTemplate.queryForMap("""
                    SELECT status, end_reason
                    FROM staff_login_session
                    WHERE session_id = ?
                    """, sessionId);
            assertThat(session.get("status")).isEqualTo("ENDED");
            assertThat(session.get("end_reason")).isEqualTo("PASSWORD_RESET");

            String storedPassword = jdbcTemplate.queryForObject(
                    "SELECT password FROM staff WHERE username = ?",
                    String.class,
                    username
            );
            assertThat(storedPassword).startsWith("$2");
            assertThat(passwordEncoder.matches("NewPassword123!", storedPassword)).isTrue();

            Integer resetNotifications = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM system_activity_notification
                    WHERE action_type = 'RESET_COMPLETE'
                      AND actor_username = ?
                    """, Integer.class, username);
            assertThat(resetNotifications).isEqualTo(1);

            mockMvc.perform(get("/reset-password").session(resetSession))
                    .andExpect(expect(status().is3xxRedirection()))
                    .andExpect(expect(redirectedUrl("/forgot-password?verificationRequired")));
        } finally {
            cleanupPasswordResetTestAccount(username);
        }
    }

    @Test
    void forgotPasswordRejectsMismatchedIdentityWithoutChangingPassword() throws Exception {
        String username = "reset.mismatch." + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = username + "@bloodbank.my";
        String storedPassword = passwordEncoder.encode("OldPassword123!");
        jdbcTemplate.update("""
                INSERT INTO staff (
                    staff_type, full_name, username, password, phone_no, ic_number,
                    gender, email, is_active, is_locked, created_at, updated_at
                )
                VALUES ('BLOOD_ADMINISTRATOR', 'Reset Mismatch Test', ?, ?, '0100000001',
                        '990101-10-9003', 'FEMALE', ?, TRUE, FALSE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, username, storedPassword, email);

        try {
            MvcResult result = mockMvc.perform(post("/forgot-password")
                            .with(csrf())
                            .param("username", username)
                            .param("email", email)
                            .param("icNumber", "990101-10-9999"))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(content().string(containsString(
                            "The request could not be completed. Please try again later."
                    ))))
                    .andReturn();

            String passwordAfterRequest = jdbcTemplate.queryForObject(
                    "SELECT password FROM staff WHERE username = ?",
                    String.class,
                    username
            );
            assertThat(passwordAfterRequest).isEqualTo(storedPassword);

            MockHttpSession resetSession = (MockHttpSession) result.getRequest().getSession(false);
            assertThat(resetSession).isNotNull();
            mockMvc.perform(get("/reset-password").session(resetSession))
                    .andExpect(expect(status().is3xxRedirection()))
                    .andExpect(expect(redirectedUrl("/forgot-password?verificationRequired")));
        } finally {
            jdbcTemplate.update("DELETE FROM staff WHERE username = ?", username);
        }
    }

    @Test
    void repeatedPasswordRecoveryMismatchesAreThrottledWithoutLoggingIdentityDetails() throws Exception {
        cleanupPasswordResetTestAccounts();
        jdbcTemplate.update("DELETE FROM authentication_throttle");
        String username = "reset.test." + UUID.randomUUID();
        String email = username + "@bloodbank.my";
        String icNumber = "990101-10-9004";
        String wrongIcNumber = "990101-10-9999";
        createPasswordResetTestAccount(
                username, email, icNumber, passwordEncoder.encode("OldPassword123!"));

        try {
            for (int attempt = 1; attempt < 5; attempt++) {
                mockMvc.perform(post("/forgot-password")
                                .with(csrf())
                                .param("username", username)
                                .param("email", email)
                                .param("icNumber", wrongIcNumber))
                        .andExpect(expect(status().isOk()));
            }

            mockMvc.perform(post("/forgot-password")
                            .with(csrf())
                            .param("username", username)
                            .param("email", email)
                            .param("icNumber", wrongIcNumber))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(header().string("Retry-After", "900")))
                    .andExpect(expect(content().string(containsString(
                            "The request could not be completed. Please try again later."
                    ))));

            mockMvc.perform(post("/forgot-password")
                            .with(csrf())
                            .param("username", username)
                            .param("email", email)
                            .param("icNumber", icNumber))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(header().string("Retry-After", "900")))
                    .andExpect(expect(content().string(containsString(
                            "The request could not be completed. Please try again later."
                    ))));

            Map<String, Object> latestFailure = latestAuditAction("PASSWORD_RECOVERY_FAILURE");
            assertThat(latestFailure.get("process_context")).asString()
                    .doesNotContain(email)
                    .doesNotContain(icNumber)
                    .doesNotContain(wrongIcNumber);
        } finally {
            jdbcTemplate.update("DELETE FROM authentication_throttle");
            cleanupPasswordResetTestAccount(username);
        }
    }

    @Test
    void authenticatedPasswordRecoveryAuditRedactsEmailAndIcNumber() throws Exception {
        cleanupAuthenticationTestAccounts();
        String username = "auth.test." + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String email = username + "@bloodbank.my";
        String icNumber = "990101-10-9002";
        createTestAdministrator(username);

        try {
            MvcResult loginResult = login(username)
                    .andExpect(expect(status().is3xxRedirection()))
                    .andReturn();
            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
            Long latestAuditId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(audit_id), 0) FROM audit_trail", Long.class);

            mockMvc.perform(post("/forgot-password")
                            .session(session)
                            .with(csrf())
                            .param("username", username)
                            .param("email", email)
                            .param("icNumber", icNumber))
                    .andExpect(expect(status().is3xxRedirection()))
                    .andExpect(expect(redirectedUrl("/reset-password")));

            List<String> recoveryAuditContexts = jdbcTemplate.queryForList("""
                    SELECT process_context::TEXT
                    FROM audit_trail
                    WHERE audit_id > ?
                      AND request_path = '/forgot-password'
                    ORDER BY audit_id
                    """, String.class, latestAuditId);
            assertThat(recoveryAuditContexts).isNotEmpty();
            assertThat(recoveryAuditContexts)
                    .allSatisfy(context -> assertThat(context)
                            .doesNotContain(email)
                            .doesNotContain(icNumber));
        } finally {
            cleanupAuthenticationTestAccounts();
        }
    }

    @Test
    void concurrentPasswordResetSubmissionsConsumeApprovalOnlyOnce() throws Exception {
        cleanupPasswordResetTestAccounts();
        String username = "reset.test." + UUID.randomUUID();
        String email = username + "@bloodbank.my";
        String icNumber = "990101-10-9005";
        createPasswordResetTestAccount(
                username, email, icNumber, passwordEncoder.encode("OldPassword123!"));

        try {
            MockHttpSession resetSession = verifyPasswordResetIdentity(username, email, icNumber);
            int requestCount = 8;
            CountDownLatch ready = new CountDownLatch(requestCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();

            try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
                for (int requestNumber = 1; requestNumber <= requestCount; requestNumber++) {
                    String password = "ConcurrentPassword" + requestNumber + "!";
                    futures.add(executor.submit(() -> submitConcurrentPasswordReset(
                            resetSession, password, ready, start)));
                }

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                List<MvcResult> results = new ArrayList<>();
                for (Future<MvcResult> future : futures) {
                    results.add(future.get(30, TimeUnit.SECONDS));
                }

                assertThat(results.stream()
                        .filter(result -> result.getResponse().getStatus() == 200)
                        .count()).isEqualTo(1);
                assertThat(results.stream()
                        .filter(result -> result.getResponse().getStatus() >= 300
                                && result.getResponse().getStatus() < 400)
                        .count()).isEqualTo(requestCount - 1L);
            }

            Integer resetNotifications = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM system_activity_notification
                    WHERE action_type = 'RESET_COMPLETE'
                      AND actor_username = ?
                    """, Integer.class, username);
            assertThat(resetNotifications).isEqualTo(1);
        } finally {
            cleanupPasswordResetTestAccount(username);
        }
    }

    @Test
    void currentPasswordRejectionKeepsFreshApprovalForAnotherAttempt() throws Exception {
        cleanupPasswordResetTestAccounts();
        String username = "reset.test." + UUID.randomUUID();
        String email = username + "@bloodbank.my";
        String icNumber = "990101-10-9006";
        createPasswordResetTestAccount(
                username, email, icNumber, passwordEncoder.encode("OldPassword123!"));

        try {
            MockHttpSession resetSession = verifyPasswordResetIdentity(username, email, icNumber);

            mockMvc.perform(post("/reset-password")
                            .session(resetSession)
                            .with(csrf())
                            .param("newPassword", "OldPassword123!")
                            .param("confirmPassword", "OldPassword123!"))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(content().string(containsString(
                            "New password must be different from the current password."
                    ))));

            mockMvc.perform(get("/reset-password").session(resetSession))
                    .andExpect(expect(status().isOk()));
        } finally {
            cleanupPasswordResetTestAccount(username);
        }
    }

    private MvcResult submitConcurrentPasswordReset(MockHttpSession session,
                                                    String password,
                                                    CountDownLatch ready,
                                                    CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent reset requests did not start together.");
        }

        return mockMvc.perform(post("/reset-password")
                        .session(session)
                        .with(csrf())
                        .param("newPassword", password)
                        .param("confirmPassword", password))
                .andReturn();
    }

    private MockHttpSession verifyPasswordResetIdentity(String username,
                                                        String email,
                                                        String icNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("username", username)
                        .param("email", email)
                        .param("icNumber", icNumber))
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/reset-password")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private Long createPasswordResetTestAccount(String username,
                                                String email,
                                                String icNumber,
                                                String storedPassword) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO staff (
                    staff_type,
                    full_name,
                    username,
                    password,
                    phone_no,
                    ic_number,
                    gender,
                    email,
                    is_active,
                    is_locked,
                    created_at,
                    updated_at
                )
                VALUES ('BLOOD_ADMINISTRATOR', 'Reset Flow Test', ?, ?, '0100000000', ?,
                        'FEMALE', ?, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING staff_id
                """, Long.class, username, storedPassword, icNumber, email);
    }

    private org.springframework.test.web.servlet.ResultActions login(String username) throws Exception {
        return mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", username)
                .param("password", "admin123"));
    }

    private void createTestAdministrator(String username) {
        Long staffId = jdbcTemplate.queryForObject("""
                INSERT INTO staff (
                    staff_type, full_name, username, password, phone_no, ic_number,
                    gender, email, is_active, is_locked, created_at, updated_at
                )
                VALUES (
                    'BLOOD_ADMINISTRATOR', 'Authentication Test', ?,
                    '$2a$10$gvxGGehUpTk5u9.8/KXbGeW0bRpZ068TwcbZpSbiEvGhxuUlPhOd6',
                    '0100000000', '990101-10-9002', 'FEMALE', ?, TRUE, FALSE,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING staff_id
                """, Long.class, username, username + "@bloodbank.my");
        jdbcTemplate.update("""
                INSERT INTO blood_administrator (staff_id, department, created_at, updated_at)
                VALUES (?, 'Test Security', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, staffId);
    }

    private void cleanupAuthenticationTestAccounts() {
        jdbcTemplate.update("DELETE FROM staff_login_session WHERE username LIKE 'auth.test.%'");
        jdbcTemplate.update("DELETE FROM staff WHERE username LIKE 'auth.test.%'");
    }

    private void cleanupPasswordResetTestAccounts() {
        jdbcTemplate.update("DELETE FROM system_activity_notification WHERE actor_username LIKE 'reset.test.%'");
        jdbcTemplate.update("DELETE FROM staff_login_session WHERE username LIKE 'reset.test.%'");
        jdbcTemplate.update("DELETE FROM staff WHERE username LIKE 'reset.test.%'");
    }

    private void cleanupPasswordResetTestAccount(String username) {
        jdbcTemplate.update("DELETE FROM system_activity_notification WHERE actor_username = ?", username);
        jdbcTemplate.update("DELETE FROM staff_login_session WHERE username = ?", username);
        jdbcTemplate.update("DELETE FROM staff WHERE username = ?", username);
    }

    private long countAuditAction(String actionType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_trail WHERE action_type = ?",
                Long.class,
                actionType
        );
        return count == null ? 0L : count;
    }

    private Map<String, Object> latestAuditAction(String actionType) {
        return jdbcTemplate.queryForMap("""
                SELECT event_category,
                       operation_type,
                       workflow_phase,
                       integrity_hash,
                       process_context::TEXT AS process_context
                FROM audit_trail
                WHERE action_type = ?
                ORDER BY audit_id DESC
                LIMIT 1
                """, actionType);
    }

    @NonNull
    private static ResultMatcher expect(ResultMatcher matcher) {
        return Objects.requireNonNull(matcher, "Result matcher must not be null.");
    }
}
