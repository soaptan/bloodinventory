package com.fyp.bloodinventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.mail.username=test.sender@gmail.com",
        "spring.mail.password=test-app-password"
})
@AutoConfigureMockMvc
class AuthenticationFlowTests {

    private static final Pattern RESET_CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    AuthenticationFlowTests(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = Objects.requireNonNull(mockMvc, "MockMvc must not be null.");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null.");
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
    void passwordResetRejectsInvalidCodeAndMismatchedPasswords() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("username", "admin")
                        .param("email", "admin@bloodbank.my")
                        .param("icNumber", "850101-10-2001")
                        .param("verificationCode", "12ab")
                        .param("newPassword", "ValidPassword123!")
                        .param("confirmPassword", "DifferentPassword123!"))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(org.hamcrest.Matchers.containsString("Enter the 6-digit verification code"))))
                .andExpect(expect(content().string(org.hamcrest.Matchers.containsString("Passwords do not match"))));
    }

    @Test
    void forgotPasswordCreatesOneUseVerificationCode() throws Exception {
        String username = "reset.test." + UUID.randomUUID();
        String email = username + "@bloodbank.my";
        String icNumber = "990101-10-9001";
        jdbcTemplate.update("""
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
                VALUES ('BLOOD_ADMINISTRATOR', 'Reset Flow Test', ?, 'oldPassword123', '0100000000', ?, 'FEMALE', ?, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, username, icNumber, email);

        try {
            clearInvocations(mailSender);
            mockMvc.perform(post("/forgot-password")
                            .with(csrf())
                            .param("username", username)
                            .param("email", email)
                            .param("icNumber", icNumber))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(content().string(org.hamcrest.Matchers.containsString("Verification code"))))
                    .andExpect(expect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Local reset link")))));

            org.mockito.ArgumentCaptor<SimpleMailMessage> mailCaptor =
                    org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(mailCaptor.capture());
            String code = extractResetCode(mailCaptor.getValue().getText());
            assertThat(code).isNotBlank();
            String storedCodeHash = jdbcTemplate.queryForObject("""
                    SELECT t.token_hash
                    FROM staff_password_reset_token t
                    JOIN staff s ON s.staff_id = t.staff_id
                    WHERE s.username = ?
                      AND t.used_at IS NULL
                    """, String.class, username);
            assertThat(storedCodeHash).startsWith("$2");

            mockMvc.perform(post("/reset-password")
                            .with(csrf())
                            .param("username", username)
                            .param("email", email)
                            .param("icNumber", icNumber)
                            .param("verificationCode", "000000")
                            .param("newPassword", "NewPassword123!")
                            .param("confirmPassword", "NewPassword123!"))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(content().string(org.hamcrest.Matchers.containsString(
                            "Verification code is invalid or expired"
                    ))));
            Integer attemptCount = jdbcTemplate.queryForObject("""
                    SELECT t.attempt_count
                    FROM staff_password_reset_token t
                    JOIN staff s ON s.staff_id = t.staff_id
                    WHERE s.username = ?
                      AND t.used_at IS NULL
                    """, Integer.class, username);
            assertThat(attemptCount).isEqualTo(1);

            mockMvc.perform(post("/reset-password")
                            .with(csrf())
                            .param("username", username)
                            .param("email", email)
                            .param("icNumber", icNumber)
                            .param("verificationCode", code)
                            .param("newPassword", "NewPassword123!")
                            .param("confirmPassword", "NewPassword123!"))
                    .andExpect(expect(status().isOk()))
                    .andExpect(expect(content().string(org.hamcrest.Matchers.containsString("Password reset successfully"))));

            Integer unusedTokens = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM staff_password_reset_token t
                    JOIN staff s ON s.staff_id = t.staff_id
                    WHERE s.username = ?
                      AND t.used_at IS NULL
                    """, Integer.class, username);
            assertThat(unusedTokens).isZero();

            String storedPassword = jdbcTemplate.queryForObject(
                    "SELECT password FROM staff WHERE username = ?",
                    String.class,
                    username
            );
            assertThat(storedPassword).startsWith("$2");
        } finally {
            jdbcTemplate.update("DELETE FROM staff WHERE username = ?", username);
        }
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

    private String extractResetCode(String emailBody) {
        Matcher matcher = RESET_CODE_PATTERN.matcher(emailBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @NonNull
    private static ResultMatcher expect(ResultMatcher matcher) {
        return Objects.requireNonNull(matcher, "Result matcher must not be null.");
    }
}
