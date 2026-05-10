package com.fyp.bloodinventory.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class NotificationDatabaseInitializer {

    @Bean
    CommandLineRunner initializeNotificationDatabaseObjects(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS system_activity_notification (
                        notification_id BIGSERIAL PRIMARY KEY,
                        module_name VARCHAR(80) NOT NULL,
                        action_type VARCHAR(20) NOT NULL,
                        message VARCHAR(255) NOT NULL,
                        actor_username VARCHAR(80),
                        actor_full_name VARCHAR(160),
                        source_ip VARCHAR(60),
                        is_read BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("ALTER TABLE system_activity_notification ADD COLUMN IF NOT EXISTS actor_full_name VARCHAR(160)");
            jdbcTemplate.execute("ALTER TABLE system_activity_notification ADD COLUMN IF NOT EXISTS source_ip VARCHAR(60)");
            jdbcTemplate.execute("ALTER TABLE system_activity_notification ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT FALSE");

            jdbcTemplate.execute("DROP PROCEDURE IF EXISTS sp_add_system_notification(VARCHAR, VARCHAR, VARCHAR, VARCHAR)");
            jdbcTemplate.execute("DROP PROCEDURE IF EXISTS sp_add_system_notification(VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR)");
            jdbcTemplate.execute("DROP PROCEDURE IF EXISTS sp_mark_all_system_notifications_read()");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_recent_system_notifications(INTEGER)");

            jdbcTemplate.execute("""
                    CREATE OR REPLACE PROCEDURE sp_add_system_notification(
                        IN p_module_name VARCHAR,
                        IN p_action_type VARCHAR,
                        IN p_message VARCHAR,
                        IN p_actor_username VARCHAR,
                        IN p_source_ip VARCHAR DEFAULT NULL
                    )
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_actor_username VARCHAR := NULLIF(TRIM(p_actor_username), '');
                        v_actor_full_name VARCHAR;
                    BEGIN
                        SELECT s.full_name
                        INTO v_actor_full_name
                        FROM staff s
                        WHERE s.username = v_actor_username
                        LIMIT 1;

                        INSERT INTO system_activity_notification (
                            module_name,
                            action_type,
                            message,
                            actor_username,
                            actor_full_name,
                            source_ip
                        )
                        VALUES (
                            COALESCE(NULLIF(TRIM(p_module_name), ''), 'System'),
                            UPPER(COALESCE(NULLIF(TRIM(p_action_type), ''), 'UPDATE')),
                            COALESCE(NULLIF(TRIM(p_message), ''), 'System activity recorded.'),
                            v_actor_username,
                            NULLIF(TRIM(COALESCE(v_actor_full_name, '')), ''),
                            NULLIF(TRIM(p_source_ip), '')
                        );
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE PROCEDURE sp_mark_all_system_notifications_read()
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        UPDATE system_activity_notification
                        SET is_read = TRUE
                        WHERE is_read = FALSE;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_recent_system_notifications(
                        IN p_limit INTEGER DEFAULT 8
                    )
                    RETURNS TABLE (
                        notification_id BIGINT,
                        module_name VARCHAR,
                        action_type VARCHAR,
                        message VARCHAR,
                        actor_username VARCHAR,
                        actor_full_name VARCHAR,
                        source_ip VARCHAR,
                        created_at TIMESTAMP,
                        is_read BOOLEAN
                    )
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_limit INTEGER := LEAST(GREATEST(COALESCE(p_limit, 8), 1), 30);
                    BEGIN
                        RETURN QUERY
                        SELECT
                            n.notification_id,
                            n.module_name,
                            n.action_type,
                            n.message,
                            n.actor_username,
                            n.actor_full_name,
                            n.source_ip,
                            n.created_at,
                            n.is_read
                        FROM system_activity_notification n
                        ORDER BY n.created_at DESC, n.notification_id DESC
                        LIMIT v_limit;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_unread_system_notification_count()
                    RETURNS BIGINT
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        RETURN (
                            SELECT COUNT(*)
                            FROM system_activity_notification
                            WHERE is_read = FALSE
                        );
                    END;
                    $$;
                    """);
        };
    }
}
