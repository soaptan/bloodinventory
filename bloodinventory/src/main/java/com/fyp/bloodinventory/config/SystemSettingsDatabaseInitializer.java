package com.fyp.bloodinventory.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SystemSettingsDatabaseInitializer {

    @Bean
    CommandLineRunner initializeSystemSettingsDatabaseObjects(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS system_setting (
                        setting_key VARCHAR(80) PRIMARY KEY,
                        setting_value VARCHAR(500) NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("ALTER TABLE system_setting ADD COLUMN IF NOT EXISTS setting_value VARCHAR(500)");
            jdbcTemplate.execute("ALTER TABLE system_setting ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

            seedSetting(jdbcTemplate, "ui_font_scale", "1.0");
            seedSetting(jdbcTemplate, "ui_accent_color", "#2f80ed");
            seedSetting(jdbcTemplate, "language_code", "en");

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS system_backup_config (
                        config_key VARCHAR(80) PRIMARY KEY,
                        backup_directory VARCHAR(500) NOT NULL DEFAULT 'backups',
                        auto_backup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                        schedule_frequency VARCHAR(20) NOT NULL DEFAULT 'DAILY',
                        schedule_time VARCHAR(5) NOT NULL DEFAULT '23:00',
                        retention_days INTEGER NOT NULL DEFAULT 30,
                        last_backup_at TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("ALTER TABLE system_backup_config ADD COLUMN IF NOT EXISTS backup_directory VARCHAR(500) NOT NULL DEFAULT 'backups'");
            jdbcTemplate.execute("ALTER TABLE system_backup_config ADD COLUMN IF NOT EXISTS auto_backup_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE system_backup_config ADD COLUMN IF NOT EXISTS schedule_frequency VARCHAR(20) NOT NULL DEFAULT 'DAILY'");
            jdbcTemplate.execute("ALTER TABLE system_backup_config ADD COLUMN IF NOT EXISTS schedule_time VARCHAR(5) NOT NULL DEFAULT '23:00'");
            jdbcTemplate.execute("ALTER TABLE system_backup_config ADD COLUMN IF NOT EXISTS retention_days INTEGER NOT NULL DEFAULT 30");
            jdbcTemplate.execute("ALTER TABLE system_backup_config ADD COLUMN IF NOT EXISTS last_backup_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE system_backup_config ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

            jdbcTemplate.update("""
                    INSERT INTO system_backup_config (
                        config_key,
                        backup_directory,
                        auto_backup_enabled,
                        schedule_frequency,
                        schedule_time,
                        retention_days,
                        updated_at
                    )
                    VALUES ('default', 'backups', FALSE, 'DAILY', '23:00', 30, CURRENT_TIMESTAMP)
                    ON CONFLICT (config_key) DO NOTHING
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS system_backup_history (
                        backup_id BIGSERIAL PRIMARY KEY,
                        trigger_type VARCHAR(20) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        file_name VARCHAR(255),
                        file_path VARCHAR(700),
                        file_size_bytes BIGINT,
                        triggered_by VARCHAR(80),
                        started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        completed_at TIMESTAMP,
                        message VARCHAR(700)
                    )
                    """);

            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL'");
            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'RUNNING'");
            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS file_name VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS file_path VARCHAR(700)");
            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT");
            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS triggered_by VARCHAR(80)");
            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE system_backup_history ADD COLUMN IF NOT EXISTS message VARCHAR(700)");

            jdbcTemplate.update("""
                    UPDATE system_backup_history
                    SET status = 'FAILED',
                        completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP),
                        message = 'Backup was interrupted before completion.'
                    WHERE status = 'RUNNING'
                    """);
        };
    }

    private void seedSetting(JdbcTemplate jdbcTemplate, String settingKey, String settingValue) {
        jdbcTemplate.update("""
                INSERT INTO system_setting (setting_key, setting_value, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (setting_key) DO NOTHING
                """, settingKey, settingValue);
    }
}
