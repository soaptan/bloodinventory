package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.AuditTrailDto;
import com.fyp.bloodinventory.dto.AuditTrailSummaryDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AuditTrailService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DISPLAY_LIMIT = 20;
    private static final int MAX_LIMIT = 500;
    private static final String DEFAULT_SORT = "newest";
    private static final String MUTATION_RECORD_FILTER = """
            UPPER(at.operation_type) IN ('INSERT', 'UPDATE', 'DELETE')
            AND LOWER(COALESCE(at.table_name, '')) <> 'staff_login_session'
            """;
    private static final String ACTOR_USERNAME_EXPRESSION = "COALESCE(NULLIF(at.username, ''), NULLIF(actor.username, ''))";
    private static final String ACTOR_ROLE_EXPRESSION = "COALESCE(NULLIF(at.role, ''), actor.staff_type::TEXT)";

    private final JdbcTemplate jdbcTemplate;

    public AuditTrailService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AuditTrailSummaryDto getSummary() {
        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*)::BIGINT AS total_events,
                    COUNT(*) FILTER (WHERE at.event_timestamp >= CURRENT_TIMESTAMP - INTERVAL '24 hours')::BIGINT AS recent_events,
                    COUNT(*) FILTER (WHERE at.event_category = 'DATA_CHANGE')::BIGINT AS data_change_events,
                    COUNT(DISTINCT COALESCE(%s, at.user_id::TEXT))::BIGINT AS active_actors
                FROM audit_trail at
                LEFT JOIN staff actor ON actor.staff_id = at.user_id
                WHERE %s
                """.formatted(ACTOR_USERNAME_EXPRESSION, MUTATION_RECORD_FILTER), (rs, rowNum) -> {
            AuditTrailSummaryDto dto = new AuditTrailSummaryDto();
            dto.setTotalEvents(rs.getLong("total_events"));
            dto.setRecentEvents(rs.getLong("recent_events"));
            dto.setDataChangeEvents(rs.getLong("data_change_events"));
            dto.setActiveActors(rs.getLong("active_actors"));
            return dto;
        });
    }

    @Transactional(readOnly = true)
    public List<AuditTrailDto> getAuditRecords(String search,
                                               String tableName,
                                               String operationType,
                                               String actionType,
                                               String role,
                                               String sortBy,
                                               Integer limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    at.audit_id,
                    at.event_timestamp,
                    TO_CHAR(at.event_timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS event_timestamp_utc,
                    at.user_id,
                    %s AS username,
                    %s AS role,
                    at.component_id,
                    at.donation_id,
                    at.event_category,
                    at.operation_type,
                    at.action_type,
                    at.table_name,
                    at.row_pk,
                    at.old_value::TEXT AS old_value,
                    at.new_value::TEXT AS new_value,
                    at.device_id,
                    at.source_ip,
                    at.location,
                    at.workflow_phase,
                    at.request_path,
                    at.http_method,
                    at.session_id_hash,
                    at.process_context::TEXT AS process_context,
                    at.previous_hash,
                    at.integrity_hash
                FROM audit_trail at
                LEFT JOIN staff actor ON actor.staff_id = at.user_id
                WHERE %s
                """.formatted(ACTOR_USERNAME_EXPRESSION, ACTOR_ROLE_EXPRESSION, MUTATION_RECORD_FILTER));

        String normalizedSearch = trimToNull(search);
        if (normalizedSearch != null) {
            sql.append("""
                    AND (
                        LOWER(COALESCE(%s, '')) LIKE ?
                        OR LOWER(COALESCE(%s, '')) LIKE ?
                        OR LOWER(COALESCE(at.event_category, '')) LIKE ?
                        OR LOWER(COALESCE(at.operation_type, '')) LIKE ?
                        OR LOWER(COALESCE(at.action_type, '')) LIKE ?
                        OR LOWER(COALESCE(at.table_name, '')) LIKE ?
                        OR LOWER(COALESCE(at.row_pk, '')) LIKE ?
                        OR LOWER(COALESCE(at.source_ip, '')) LIKE ?
                        OR LOWER(COALESCE(at.workflow_phase, '')) LIKE ?
                        OR LOWER(COALESCE(at.request_path, '')) LIKE ?
                        OR LOWER(COALESCE(at.http_method, '')) LIKE ?
                        OR LOWER(COALESCE(at.process_context::TEXT, '')) LIKE ?
                        OR LOWER(COALESCE(at.integrity_hash, '')) LIKE ?
                    )
                    """.formatted(ACTOR_USERNAME_EXPRESSION, ACTOR_ROLE_EXPRESSION));
            String like = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
            for (int index = 0; index < 13; index++) {
                params.add(like);
            }
        }

        addExactFilter(sql, params, "at.table_name", tableName);
        addExactFilter(sql, params, "at.operation_type", operationType);
        addExactFilter(sql, params, "at.action_type", actionType);
        addExactFilter(sql, params, ACTOR_ROLE_EXPRESSION, role);

        sql.append(" ORDER BY ").append(orderBy(sortBy));
        sql.append(" LIMIT ?");
        params.add(normalizeLimit(limit));

        return jdbcTemplate.query(sql.toString(), this::mapAuditTrail, params.toArray());
    }

    @Transactional(readOnly = true)
    public List<String> getTableNames() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT table_name
                FROM audit_trail at
                WHERE %s
                ORDER BY table_name ASC
                """.formatted(MUTATION_RECORD_FILTER), String.class);
    }

    @Transactional(readOnly = true)
    public List<String> getActionTypes() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT action_type
                FROM audit_trail at
                WHERE %s
                ORDER BY action_type ASC
                """.formatted(MUTATION_RECORD_FILTER), String.class);
    }

    @Transactional(readOnly = true)
    public List<String> getOperationTypes() {
        return List.of("INSERT", "UPDATE", "DELETE");
    }

    @Transactional(readOnly = true)
    public List<String> getRoles() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT %s AS role
                FROM audit_trail at
                LEFT JOIN staff actor ON actor.staff_id = at.user_id
                WHERE %s IS NOT NULL
                  AND %s
                ORDER BY role ASC
                """.formatted(ACTOR_ROLE_EXPRESSION, ACTOR_ROLE_EXPRESSION, MUTATION_RECORD_FILTER), String.class);
    }

    public int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        return Math.min(MAX_LIMIT, Math.max(1, limit));
    }

    public int latestDisplayLimit() {
        return DISPLAY_LIMIT;
    }

    public String latestDisplaySort() {
        return DEFAULT_SORT;
    }

    public String normalizeSort(String sortBy) {
        if (sortBy == null) {
            return DEFAULT_SORT;
        }

        return switch (sortBy) {
            case "newest", "oldest", "actor", "table" -> sortBy;
            default -> DEFAULT_SORT;
        };
    }

    private void addExactFilter(StringBuilder sql, List<Object> params, String columnName, String value) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.startsWith("all")) {
            return;
        }

        sql.append(" AND ").append(columnName).append(" = ?");
        params.add(normalized);
    }

    private String orderBy(String sortBy) {
        return switch (normalizeSort(sortBy)) {
            case "oldest" -> "at.event_timestamp ASC, at.audit_id ASC";
            case "actor" -> "username ASC NULLS LAST, at.event_timestamp DESC, at.audit_id DESC";
            case "table" -> "at.table_name ASC, at.event_timestamp DESC, at.audit_id DESC";
            default -> "at.event_timestamp DESC, at.audit_id DESC";
        };
    }

    private AuditTrailDto mapAuditTrail(ResultSet rs, int rowNum) throws SQLException {
        AuditTrailDto dto = new AuditTrailDto();
        dto.setAuditId(nullableLong(rs, "audit_id"));
        dto.setEventTimestamp(rs.getTimestamp("event_timestamp"));
        dto.setEventTimestampUtc(rs.getString("event_timestamp_utc"));
        dto.setUserId(nullableLong(rs, "user_id"));
        dto.setUsername(rs.getString("username"));
        dto.setRole(rs.getString("role"));
        dto.setComponentId(nullableLong(rs, "component_id"));
        dto.setDonationId(nullableLong(rs, "donation_id"));
        dto.setEventCategory(rs.getString("event_category"));
        dto.setOperationType(rs.getString("operation_type"));
        dto.setActionType(rs.getString("action_type"));
        dto.setTableName(rs.getString("table_name"));
        dto.setRowPk(rs.getString("row_pk"));
        dto.setOldValue(rs.getString("old_value"));
        dto.setNewValue(rs.getString("new_value"));
        dto.setDeviceId(rs.getString("device_id"));
        dto.setSourceIp(rs.getString("source_ip"));
        dto.setLocation(rs.getString("location"));
        dto.setWorkflowPhase(rs.getString("workflow_phase"));
        dto.setRequestPath(rs.getString("request_path"));
        dto.setHttpMethod(rs.getString("http_method"));
        dto.setSessionIdHash(rs.getString("session_id_hash"));
        dto.setProcessContext(rs.getString("process_context"));
        dto.setPreviousHash(rs.getString("previous_hash"));
        dto.setIntegrityHash(rs.getString("integrity_hash"));
        return dto;
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
