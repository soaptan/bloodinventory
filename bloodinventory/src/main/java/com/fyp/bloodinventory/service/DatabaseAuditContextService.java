package com.fyp.bloodinventory.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class DatabaseAuditContextService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseAuditContextService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void applyCurrentContext() {
        AuditActor actor = currentActor();
        RequestDetails requestDetails = currentRequestDetails();

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            applyContext(connection, actor, requestDetails, true);
            return null;
        });
    }

    public <T> T executeWithCurrentContext(ConnectionCallback<T> callback) {
        AuditActor actor = currentActor();
        RequestDetails requestDetails = currentRequestDetails();

        return jdbcTemplate.execute((ConnectionCallback<T>) connection -> {
            applyContext(connection, actor, requestDetails, false);
            try {
                return callback.doInConnection(connection);
            } finally {
                clearContext(connection);
            }
        });
    }

    private void applyContext(Connection connection,
                              AuditActor actor,
                              RequestDetails requestDetails,
                              boolean transactionLocal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    set_config('bloodinventory.current_user_id', ?, ?),
                    set_config('bloodinventory.current_username', ?, ?),
                    set_config('bloodinventory.current_user_role', ?, ?),
                    set_config('bloodinventory.current_device_id', ?, ?),
                    set_config('bloodinventory.current_source_ip', ?, ?),
                    set_config('bloodinventory.current_request_path', ?, ?),
                    set_config('bloodinventory.current_http_method', ?, ?),
                    set_config('bloodinventory.current_session_id_hash', ?, ?)
                """)) {
            statement.setString(1, valueOrEmpty(actor.userId() == null ? null : actor.userId().toString()));
            statement.setBoolean(2, transactionLocal);
            statement.setString(3, valueOrEmpty(actor.username()));
            statement.setBoolean(4, transactionLocal);
            statement.setString(5, valueOrEmpty(actor.role()));
            statement.setBoolean(6, transactionLocal);
            statement.setString(7, valueOrEmpty(requestDetails.deviceId()));
            statement.setBoolean(8, transactionLocal);
            statement.setString(9, valueOrEmpty(requestDetails.sourceIp()));
            statement.setBoolean(10, transactionLocal);
            statement.setString(11, valueOrEmpty(requestDetails.requestPath()));
            statement.setBoolean(12, transactionLocal);
            statement.setString(13, valueOrEmpty(requestDetails.httpMethod()));
            statement.setBoolean(14, transactionLocal);
            statement.setString(15, valueOrEmpty(requestDetails.sessionIdHash()));
            statement.setBoolean(16, transactionLocal);
            statement.execute();
        }
    }

    private void clearContext(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    set_config('bloodinventory.current_user_id', '', FALSE),
                    set_config('bloodinventory.current_username', '', FALSE),
                    set_config('bloodinventory.current_user_role', '', FALSE),
                    set_config('bloodinventory.current_device_id', '', FALSE),
                    set_config('bloodinventory.current_source_ip', '', FALSE),
                    set_config('bloodinventory.current_request_path', '', FALSE),
                    set_config('bloodinventory.current_http_method', '', FALSE),
                    set_config('bloodinventory.current_session_id_hash', '', FALSE)
                """)) {
            statement.execute();
        }
    }

    private AuditActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isSignedIn(authentication)) {
            return new AuditActor(null, "system", "SYSTEM");
        }

        String username = safeTrim(authentication.getName());
        String authorityRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("USER");

        try {
            return jdbcTemplate.queryForObject("""
                    SELECT staff_id, username, staff_type
                    FROM staff
                    WHERE LOWER(username) = LOWER(?)
                    """, (rs, rowNum) -> new AuditActor(
                    rs.getLong("staff_id"),
                    rs.getString("username"),
                    rs.getString("staff_type")
            ), username);
        } catch (EmptyResultDataAccessException ex) {
            return new AuditActor(null, username, authorityRole);
        }
    }

    private RequestDetails currentRequestDetails() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new RequestDetails("application", null, null, null, null);
        }

        HttpServletRequest request = attributes.getRequest();
        String deviceId = safeTrim(request.getHeader("X-Device-Id"));
        if (deviceId == null) {
            deviceId = safeTrim(request.getHeader("User-Agent"));
        }
        if (deviceId == null) {
            deviceId = "web-application";
        }

        String sourceIp = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (sourceIp == null) {
            sourceIp = safeTrim(request.getRemoteAddr());
        }

        String requestPath = safeTrim(request.getRequestURI());
        String httpMethod = safeTrim(request.getMethod());
        String sessionIdHash = null;
        try {
            if (request.getSession(false) != null) {
                sessionIdHash = sha256Hex(request.getSession(false).getId());
            }
        } catch (IllegalStateException ignored) {
            sessionIdHash = null;
        }

        return new RequestDetails(
                truncate(deviceId, 120),
                truncate(sourceIp, 80),
                truncate(requestPath, 255),
                truncate(httpMethod, 12),
                sessionIdHash
        );
    }

    private String firstForwardedIp(String forwardedFor) {
        String normalized = safeTrim(forwardedFor);
        if (normalized == null) {
            return null;
        }

        return List.of(normalized.split(",")).stream()
                .map(this::safeTrim)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }

    private String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
    }

    private record AuditActor(Long userId, String username, String role) {
    }

    private record RequestDetails(String deviceId, String sourceIp, String requestPath, String httpMethod, String sessionIdHash) {
    }
}
