package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.SystemNotificationDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.List;

@Service
public class SystemNotificationService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseAuditContextService auditContextService;

    public SystemNotificationService(JdbcTemplate jdbcTemplate,
                                     DatabaseAuditContextService auditContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditContextService = auditContextService;
    }

    public void record(String moduleName, String actionType, String message, String actorUsername) {
        record(moduleName, actionType, message, actorUsername, null);
    }

    public void record(String moduleName, String actionType, String message, String actorUsername, String sourceIp) {
        auditContextService.executeWithCurrentContext(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("CALL sp_add_system_notification(?, ?, ?, ?, ?)")) {
                statement.setString(1, moduleName);
                statement.setString(2, actionType);
                statement.setString(3, message);
                statement.setString(4, actorUsername);
                statement.setString(5, sourceIp);
                statement.execute();
            }
            return null;
        });
    }

    public List<SystemNotificationDto> getRecentNotifications() {
        return getRecentNotifications(8);
    }

    public List<SystemNotificationDto> getRecentNotifications(int limit) {
        String sql = "SELECT * FROM fn_recent_system_notifications(?)";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            SystemNotificationDto dto = new SystemNotificationDto();
            dto.setNotificationId(rs.getLong("notification_id"));
            dto.setModuleName(rs.getString("module_name"));
            dto.setActionType(rs.getString("action_type"));
            dto.setMessage(rs.getString("message"));
            dto.setActorUsername(rs.getString("actor_username"));
            dto.setActorFullName(rs.getString("actor_full_name"));
            dto.setSourceIp(rs.getString("source_ip"));
            dto.setCreatedAt(rs.getTimestamp("created_at"));
            dto.setRead(rs.getBoolean("is_read"));
            return dto;
        }, limit);
    }

    public long countUnreadNotifications() {
        Long result = jdbcTemplate.queryForObject(
                "SELECT fn_unread_system_notification_count()",
                Long.class
        );
        return result == null ? 0L : result;
    }

    public void markAllAsRead() {
        auditContextService.executeWithCurrentContext(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("CALL sp_mark_all_system_notifications_read()")) {
                statement.execute();
            }
            return null;
        });
    }
}
