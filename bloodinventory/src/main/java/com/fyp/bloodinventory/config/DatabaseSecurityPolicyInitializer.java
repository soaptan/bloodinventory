package com.fyp.bloodinventory.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("legacy-db-init")
public class DatabaseSecurityPolicyInitializer {

    @Bean
    CommandLineRunner initializeDatabaseSecurityPolicyObjects(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS system_security_policy (
                        policy_key VARCHAR(80) PRIMARY KEY,
                        session_control_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        max_concurrent_sessions INTEGER NOT NULL DEFAULT 1,
                        session_timeout_minutes INTEGER NOT NULL DEFAULT 15,
                        prevent_new_login BOOLEAN NOT NULL DEFAULT FALSE,
                        row_level_security_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE system_security_policy
                    ADD COLUMN IF NOT EXISTS session_control_enabled BOOLEAN NOT NULL DEFAULT TRUE
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE system_security_policy
                    ADD COLUMN IF NOT EXISTS max_concurrent_sessions INTEGER NOT NULL DEFAULT 1
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE system_security_policy
                    ADD COLUMN IF NOT EXISTS session_timeout_minutes INTEGER NOT NULL DEFAULT 15
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE system_security_policy
                    ADD COLUMN IF NOT EXISTS prevent_new_login BOOLEAN NOT NULL DEFAULT FALSE
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE system_security_policy
                    ADD COLUMN IF NOT EXISTS row_level_security_enabled BOOLEAN NOT NULL DEFAULT TRUE
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE system_security_policy
                    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    """);

            jdbcTemplate.update("""
                    INSERT INTO system_security_policy (
                        policy_key,
                        session_control_enabled,
                        max_concurrent_sessions,
                        session_timeout_minutes,
                        prevent_new_login,
                        row_level_security_enabled,
                        updated_at
                    )
                    VALUES ('default', TRUE, 1, 15, FALSE, TRUE, CURRENT_TIMESTAMP)
                    ON CONFLICT (policy_key) DO NOTHING
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS staff_login_session (
                        session_id VARCHAR(128) PRIMARY KEY,
                        username VARCHAR(80) NOT NULL,
                        source_ip VARCHAR(80),
                        user_agent VARCHAR(255),
                        status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        expires_at TIMESTAMP,
                        ended_at TIMESTAMP,
                        end_reason VARCHAR(80)
                    )
                    """);

            jdbcTemplate.execute("ALTER TABLE staff_login_session ADD COLUMN IF NOT EXISTS source_ip VARCHAR(80)");
            jdbcTemplate.execute("ALTER TABLE staff_login_session ADD COLUMN IF NOT EXISTS user_agent VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE staff_login_session ADD COLUMN IF NOT EXISTS status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'");
            jdbcTemplate.execute("ALTER TABLE staff_login_session ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE staff_login_session ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE staff_login_session ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE staff_login_session ADD COLUMN IF NOT EXISTS ended_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE staff_login_session ADD COLUMN IF NOT EXISTS end_reason VARCHAR(80)");

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_expire_stale_staff_sessions()
                    RETURNS VOID
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        UPDATE staff_login_session s
                        SET status = 'EXPIRED',
                            ended_at = CURRENT_TIMESTAMP,
                            end_reason = 'TIMEOUT'
                        FROM system_security_policy p
                        WHERE p.policy_key = 'default'
                          AND p.session_control_enabled = TRUE
                          AND s.status = 'ACTIVE'
                          AND (
                              s.last_seen_at
                              + ((GREATEST(COALESCE(p.session_timeout_minutes, 15), 1) || ' minutes')::INTERVAL)
                          ) < CURRENT_TIMESTAMP;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_register_staff_session(
                        p_username VARCHAR,
                        p_session_id VARCHAR,
                        p_source_ip VARCHAR,
                        p_user_agent VARCHAR
                    )
                    RETURNS BOOLEAN
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_session_control_enabled BOOLEAN := TRUE;
                        v_max_sessions INTEGER := 1;
                        v_timeout_minutes INTEGER := 15;
                        v_prevent_new_login BOOLEAN := FALSE;
                        v_active_count INTEGER := 0;
                        v_expire_count INTEGER := 0;
                    BEGIN
                        SELECT
                            session_control_enabled,
                            GREATEST(COALESCE(max_concurrent_sessions, 1), 0),
                            GREATEST(COALESCE(session_timeout_minutes, 15), 1),
                            prevent_new_login
                        INTO
                            v_session_control_enabled,
                            v_max_sessions,
                            v_timeout_minutes,
                            v_prevent_new_login
                        FROM system_security_policy
                        WHERE policy_key = 'default';

                        PERFORM fn_expire_stale_staff_sessions();

                        IF COALESCE(v_session_control_enabled, TRUE) = TRUE AND v_max_sessions > 0 THEN
                            SELECT COUNT(*)
                            INTO v_active_count
                            FROM staff_login_session
                            WHERE username = p_username
                              AND session_id <> p_session_id
                              AND status = 'ACTIVE';

                            IF v_active_count >= v_max_sessions THEN
                                IF COALESCE(v_prevent_new_login, FALSE) = TRUE THEN
                                    INSERT INTO staff_login_session (
                                        session_id,
                                        username,
                                        source_ip,
                                        user_agent,
                                        status,
                                        created_at,
                                        last_seen_at,
                                        expires_at,
                                        ended_at,
                                        end_reason
                                    )
                                    VALUES (
                                        p_session_id,
                                        p_username,
                                        p_source_ip,
                                        LEFT(COALESCE(p_user_agent, ''), 255),
                                        'BLOCKED',
                                        CURRENT_TIMESTAMP,
                                        CURRENT_TIMESTAMP,
                                        CURRENT_TIMESTAMP,
                                        CURRENT_TIMESTAMP,
                                        'MAX_SESSIONS'
                                    )
                                    ON CONFLICT (session_id) DO UPDATE
                                    SET username = EXCLUDED.username,
                                        source_ip = EXCLUDED.source_ip,
                                        user_agent = EXCLUDED.user_agent,
                                        status = 'BLOCKED',
                                        last_seen_at = CURRENT_TIMESTAMP,
                                        expires_at = CURRENT_TIMESTAMP,
                                        ended_at = CURRENT_TIMESTAMP,
                                        end_reason = 'MAX_SESSIONS';

                                    RETURN FALSE;
                                END IF;

                                v_expire_count := v_active_count - v_max_sessions + 1;

                                UPDATE staff_login_session
                                SET status = 'SUPERSEDED',
                                    ended_at = CURRENT_TIMESTAMP,
                                    end_reason = 'MAX_SESSIONS'
                                WHERE session_id IN (
                                    SELECT session_id
                                    FROM staff_login_session
                                    WHERE username = p_username
                                      AND session_id <> p_session_id
                                      AND status = 'ACTIVE'
                                    ORDER BY last_seen_at ASC, created_at ASC
                                    LIMIT v_expire_count
                                );
                            END IF;
                        END IF;

                        INSERT INTO staff_login_session (
                            session_id,
                            username,
                            source_ip,
                            user_agent,
                            status,
                            created_at,
                            last_seen_at,
                            expires_at,
                            ended_at,
                            end_reason
                        )
                        VALUES (
                            p_session_id,
                            p_username,
                            p_source_ip,
                            LEFT(COALESCE(p_user_agent, ''), 255),
                            'ACTIVE',
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP + ((v_timeout_minutes || ' minutes')::INTERVAL),
                            NULL,
                            NULL
                        )
                        ON CONFLICT (session_id) DO UPDATE
                        SET username = EXCLUDED.username,
                            source_ip = EXCLUDED.source_ip,
                            user_agent = EXCLUDED.user_agent,
                            status = 'ACTIVE',
                            last_seen_at = CURRENT_TIMESTAMP,
                            expires_at = CURRENT_TIMESTAMP + ((v_timeout_minutes || ' minutes')::INTERVAL),
                            ended_at = NULL,
                            end_reason = NULL;

                        RETURN TRUE;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_touch_staff_session(
                        p_username VARCHAR,
                        p_session_id VARCHAR
                    )
                    RETURNS BOOLEAN
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_session_control_enabled BOOLEAN := TRUE;
                        v_timeout_minutes INTEGER := 15;
                    BEGIN
                        SELECT
                            session_control_enabled,
                            GREATEST(COALESCE(session_timeout_minutes, 15), 1)
                        INTO
                            v_session_control_enabled,
                            v_timeout_minutes
                        FROM system_security_policy
                        WHERE policy_key = 'default';

                        IF COALESCE(v_session_control_enabled, TRUE) = FALSE THEN
                            RETURN TRUE;
                        END IF;

                        PERFORM fn_expire_stale_staff_sessions();

                        UPDATE staff_login_session
                        SET last_seen_at = CURRENT_TIMESTAMP,
                            expires_at = CURRENT_TIMESTAMP + ((v_timeout_minutes || ' minutes')::INTERVAL)
                        WHERE username = p_username
                          AND session_id = p_session_id
                          AND status = 'ACTIVE';

                        RETURN FOUND;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE PROCEDURE sp_end_staff_session(
                        IN p_session_id VARCHAR,
                        IN p_reason VARCHAR
                    )
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        UPDATE staff_login_session
                        SET status = CASE
                                WHEN status = 'ACTIVE' THEN 'ENDED'
                                ELSE status
                            END,
                            ended_at = COALESCE(ended_at, CURRENT_TIMESTAMP),
                            end_reason = CASE
                                WHEN status = 'ACTIVE' THEN COALESCE(NULLIF(TRIM(p_reason), ''), 'ENDED')
                                ELSE COALESCE(end_reason, NULLIF(TRIM(p_reason), ''), 'ENDED')
                            END
                        WHERE session_id = p_session_id;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_staff_accessible_module_keys(p_staff_type VARCHAR)
                    RETURNS TABLE(module_key VARCHAR)
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_row_level_security_enabled BOOLEAN := TRUE;
                    BEGIN
                        SELECT row_level_security_enabled
                        INTO v_row_level_security_enabled
                        FROM system_security_policy
                        WHERE policy_key = 'default';

                        IF COALESCE(v_row_level_security_enabled, TRUE) = FALSE THEN
                            RETURN QUERY
                            SELECT ranked.module_key
                            FROM (
                                SELECT
                                    access.module_key,
                                    MIN(access.sort_order) AS sort_order
                                FROM staff_module_access access
                                WHERE access.is_enabled = TRUE
                                GROUP BY access.module_key
                            ) ranked
                            ORDER BY ranked.sort_order ASC, ranked.module_key ASC;
                            RETURN;
                        END IF;

                        RETURN QUERY
                        SELECT access.module_key
                        FROM staff_module_access access
                        WHERE access.staff_type = p_staff_type
                          AND access.is_enabled = TRUE
                        ORDER BY access.sort_order ASC, access.module_key ASC;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_staff_can_access_path(
                        p_staff_type VARCHAR,
                        p_request_path VARCHAR
                    )
                    RETURNS BOOLEAN
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_row_level_security_enabled BOOLEAN := TRUE;
                    BEGIN
                        SELECT row_level_security_enabled
                        INTO v_row_level_security_enabled
                        FROM system_security_policy
                        WHERE policy_key = 'default';

                        IF COALESCE(v_row_level_security_enabled, TRUE) = FALSE THEN
                            RETURN TRUE;
                        END IF;

                        RETURN EXISTS (
                            SELECT 1
                            FROM staff_module_access access
                            WHERE access.staff_type = p_staff_type
                              AND access.is_enabled = TRUE
                              AND (
                                  p_request_path = access.url_pattern
                                  OR (
                                      RIGHT(access.url_pattern, 3) = '/**'
                                      AND (
                                          p_request_path = LEFT(access.url_pattern, LENGTH(access.url_pattern) - 3)
                                          OR p_request_path LIKE (LEFT(access.url_pattern, LENGTH(access.url_pattern) - 2) || '%')
                                      )
                                  )
                                  OR p_request_path LIKE REPLACE(REPLACE(access.url_pattern, '**', '%'), '*', '%')
                              )
                        );
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE staff_module_access ENABLE ROW LEVEL SECURITY
                    """);

            jdbcTemplate.execute("""
                    DROP POLICY IF EXISTS staff_module_access_by_staff_type ON staff_module_access
                    """);

            jdbcTemplate.execute("""
                    CREATE POLICY staff_module_access_by_staff_type
                    ON staff_module_access
                    FOR SELECT
                    USING (
                        current_setting('bloodinventory.staff_type', TRUE) IS NULL
                        OR staff_type = current_setting('bloodinventory.staff_type', TRUE)
                    )
                    """);
        };
    }
}
