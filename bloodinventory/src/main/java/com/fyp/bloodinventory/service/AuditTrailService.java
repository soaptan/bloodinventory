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

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final JdbcTemplate jdbcTemplate;

    public AuditTrailService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AuditTrailSummaryDto getSummary() {
        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*)::BIGINT AS total_events,
                    COUNT(*) FILTER (WHERE event_timestamp >= CURRENT_TIMESTAMP - INTERVAL '24 hours')::BIGINT AS recent_events,
                    COUNT(*) FILTER (WHERE component_id IS NOT NULL)::BIGINT AS component_events,
                    COUNT(DISTINCT COALESCE(username, user_id::TEXT))::BIGINT AS active_actors
                FROM audit_trail
                """, (rs, rowNum) -> {
            AuditTrailSummaryDto dto = new AuditTrailSummaryDto();
            dto.setTotalEvents(rs.getLong("total_events"));
            dto.setRecentEvents(rs.getLong("recent_events"));
            dto.setComponentEvents(rs.getLong("component_events"));
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
                    audit_id,
                    event_timestamp,
                    TO_CHAR(event_timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS event_timestamp_utc,
                    user_id,
                    username,
                    role,
                    component_id,
                    donation_id,
                    event_category,
                    operation_type,
                    action_type,
                    table_name,
                    row_pk,
                    old_value::TEXT AS old_value,
                    new_value::TEXT AS new_value,
                    device_id,
                    source_ip,
                    location,
                    workflow_phase,
                    request_path,
                    http_method,
                    session_id_hash,
                    process_context::TEXT AS process_context,
                    previous_hash,
                    integrity_hash
                FROM audit_trail
                WHERE 1 = 1
                """);

        String normalizedSearch = trimToNull(search);
        if (normalizedSearch != null) {
            sql.append("""
                    AND (
                        LOWER(COALESCE(username, '')) LIKE ?
                        OR LOWER(COALESCE(role, '')) LIKE ?
                        OR LOWER(COALESCE(event_category, '')) LIKE ?
                        OR LOWER(COALESCE(operation_type, '')) LIKE ?
                        OR LOWER(COALESCE(action_type, '')) LIKE ?
                        OR LOWER(COALESCE(table_name, '')) LIKE ?
                        OR COALESCE(component_id::TEXT, '') LIKE ?
                        OR COALESCE(donation_id::TEXT, '') LIKE ?
                        OR LOWER(COALESCE(row_pk, '')) LIKE ?
                        OR LOWER(COALESCE(location, '')) LIKE ?
                        OR LOWER(COALESCE(device_id, '')) LIKE ?
                        OR LOWER(COALESCE(source_ip, '')) LIKE ?
                        OR LOWER(COALESCE(workflow_phase, '')) LIKE ?
                        OR LOWER(COALESCE(request_path, '')) LIKE ?
                        OR LOWER(COALESCE(http_method, '')) LIKE ?
                        OR LOWER(COALESCE(process_context::TEXT, '')) LIKE ?
                        OR LOWER(COALESCE(integrity_hash, '')) LIKE ?
                    )
                    """);
            String like = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
            for (int index = 0; index < 17; index++) {
                params.add(like);
            }
        }

        addExactFilter(sql, params, "table_name", tableName);
        addExactFilter(sql, params, "operation_type", operationType);
        addExactFilter(sql, params, "action_type", actionType);
        addExactFilter(sql, params, "role", role);

        sql.append(" ORDER BY ").append(orderBy(sortBy));
        sql.append(" LIMIT ?");
        params.add(normalizeLimit(limit));

        return jdbcTemplate.query(sql.toString(), this::mapAuditTrail, params.toArray());
    }

    @Transactional(readOnly = true)
    public List<String> getTableNames() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT table_name
                FROM audit_trail
                ORDER BY table_name ASC
                """, String.class);
    }

    @Transactional(readOnly = true)
    public List<String> getActionTypes() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT action_type
                FROM audit_trail
                ORDER BY action_type ASC
                """, String.class);
    }

    @Transactional(readOnly = true)
    public List<String> getOperationTypes() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT operation_type
                FROM audit_trail
                ORDER BY operation_type ASC
                """, String.class);
    }

    @Transactional(readOnly = true)
    public List<String> getRoles() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT role
                FROM audit_trail
                WHERE role IS NOT NULL
                ORDER BY role ASC
                """, String.class);
    }

    public int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        return Math.min(MAX_LIMIT, Math.max(25, limit));
    }

    public String normalizeSort(String sortBy) {
        if (sortBy == null) {
            return "newest";
        }

        return switch (sortBy) {
            case "newest", "oldest", "actor", "table", "component" -> sortBy;
            default -> "newest";
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
            case "oldest" -> "event_timestamp ASC, audit_id ASC";
            case "actor" -> "username ASC NULLS LAST, event_timestamp DESC, audit_id DESC";
            case "table" -> "table_name ASC, event_timestamp DESC, audit_id DESC";
            case "component" -> "component_id ASC NULLS LAST, event_timestamp DESC, audit_id DESC";
            default -> "event_timestamp DESC, audit_id DESC";
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
