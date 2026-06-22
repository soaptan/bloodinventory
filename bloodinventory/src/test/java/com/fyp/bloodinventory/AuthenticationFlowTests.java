package com.fyp.bloodinventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.Objects;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowTests {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AuthenticationFlowTests(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = Objects.requireNonNull(mockMvc, "MockMvc must not be null.");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null.");
    }

    @Test
    void manualLogoutAllowsCleanSubsequentLogin() throws Exception {
        MvcResult firstLogin = login()
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/admin/dashboard")))
                .andReturn();
        MockHttpSession session = (MockHttpSession) firstLogin.getRequest().getSession(false);

        mockMvc.perform(post("/logout")
                        .with(csrf())
                        .session(session))
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/login?logout")));

        login()
                .andExpect(expect(status().is3xxRedirection()))
                .andExpect(expect(redirectedUrl("/admin/dashboard")));
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

    private org.springframework.test.web.servlet.ResultActions login() throws Exception {
        return mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "admin123"));
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
