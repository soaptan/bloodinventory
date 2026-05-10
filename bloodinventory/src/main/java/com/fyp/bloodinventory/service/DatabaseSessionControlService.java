package com.fyp.bloodinventory.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseSessionControlService {

    private static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 15;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSessionControlService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean registerSuccessfulLogin(String username, HttpServletRequest request) {
        HttpSession session = request.getSession();
        applyDatabaseTimeout(session);

        Boolean allowed = jdbcTemplate.queryForObject(
                "SELECT fn_register_staff_session(?, ?, ?, ?)",
                Boolean.class,
                username,
                session.getId(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );

        return Boolean.TRUE.equals(allowed);
    }

    public boolean validateAndTouch(String username, HttpSession session) {
        if (session == null) {
            return false;
        }

        applyDatabaseTimeout(session);

        Boolean active = jdbcTemplate.queryForObject(
                "SELECT fn_touch_staff_session(?, ?)",
                Boolean.class,
                username,
                session.getId()
        );

        return Boolean.TRUE.equals(active);
    }

    public void endSession(HttpSession session, String reason) {
        if (session == null) {
            return;
        }

        endSession(session.getId(), reason);
    }

    public void endSession(String sessionId, String reason) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        jdbcTemplate.update("CALL sp_end_staff_session(?, ?)", sessionId, reason);
    }

    private void applyDatabaseTimeout(HttpSession session) {
        int timeoutMinutes = currentSessionTimeoutMinutes();
        session.setMaxInactiveInterval(timeoutMinutes * 60);
    }

    private int currentSessionTimeoutMinutes() {
        Integer timeoutMinutes = jdbcTemplate.queryForObject("""
                SELECT GREATEST(COALESCE(session_timeout_minutes, ?), 1)
                FROM system_security_policy
                WHERE policy_key = 'default'
                """, Integer.class, DEFAULT_SESSION_TIMEOUT_MINUTES);

        return timeoutMinutes == null ? DEFAULT_SESSION_TIMEOUT_MINUTES : timeoutMinutes;
    }
}
