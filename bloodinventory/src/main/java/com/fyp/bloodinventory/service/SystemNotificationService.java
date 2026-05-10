package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.SystemNotificationDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemNotificationService {

    private final JdbcTemplate jdbcTemplate;

    public SystemNotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String moduleName, String actionType, String message, String actorUsername) {
        record(moduleName, actionType, message, actorUsername, null);
    }

    public void record(String moduleName, String actionType, String message, String actorUsername, String sourceIp) {
        jdbcTemplate.update(
                "CALL sp_add_system_notification(?, ?, ?, ?, ?)",
                moduleName,
                actionType,
                message,
                actorUsername,
                sourceIp
        );
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
        jdbcTemplate.update("CALL sp_mark_all_system_notifications_read()");
    }
}
