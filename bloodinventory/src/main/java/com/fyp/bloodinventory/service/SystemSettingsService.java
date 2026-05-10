package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.BackupHistoryDto;
import com.fyp.bloodinventory.dto.BackupSettingsRequest;
import com.fyp.bloodinventory.dto.LanguageSettingsRequest;
import com.fyp.bloodinventory.dto.SecuritySettingsRequest;
import com.fyp.bloodinventory.dto.SystemUiSettingsRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
public class SystemSettingsService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern SCHEDULE_TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final List<String> ALLOWED_FREQUENCIES = List.of("DAILY", "WEEKLY");
    private static final Map<String, String> ALLOWED_LANGUAGES = Map.of(
            "en", "English",
            "ms", "Bahasa Malaysia",
            "zh", "Chinese"
    );

    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);

    public SystemSettingsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SystemUiSettingsRequest getUiSettings() {
        SystemUiSettingsRequest request = new SystemUiSettingsRequest();
        request.setFontScale(parseDouble(settingValue("ui_font_scale", "1.0"), 1.0));
        request.setAccentColor(sanitizeColor(settingValue("ui_accent_color", "#2f80ed")));
        return request;
    }

    public void updateUiSettings(SystemUiSettingsRequest request) {
        double fontScale = clamp(request.getFontScale(), 0.9, 1.25);
        String accentColor = sanitizeColor(request.getAccentColor());
        upsertSetting("ui_font_scale", String.format(Locale.US, "%.2f", fontScale));
        upsertSetting("ui_accent_color", accentColor);
    }

    public LanguageSettingsRequest getLanguageSettings() {
        LanguageSettingsRequest request = new LanguageSettingsRequest();
        request.setLanguageCode(sanitizeLanguage(settingValue("language_code", "en")));
        return request;
    }

    public void updateLanguageSettings(LanguageSettingsRequest request) {
        upsertSetting("language_code", sanitizeLanguage(request.getLanguageCode()));
    }

    public Map<String, String> getLanguageOptions() {
        return ALLOWED_LANGUAGES;
    }

    public BackupSettingsRequest getBackupSettings() {
        return jdbcTemplate.queryForObject("""
                SELECT backup_directory,
                       auto_backup_enabled,
                       schedule_frequency,
                       schedule_time,
                       retention_days
                FROM system_backup_config
                WHERE config_key = 'default'
                """, (rs, rowNum) -> {
            BackupSettingsRequest request = new BackupSettingsRequest();
            request.setBackupDirectory(rs.getString("backup_directory"));
            request.setAutoBackupEnabled(rs.getBoolean("auto_backup_enabled"));
            request.setScheduleFrequency(normalizeFrequency(rs.getString("schedule_frequency")));
            request.setScheduleTime(normalizeScheduleTime(rs.getString("schedule_time")));
            request.setRetentionDays(Math.max(rs.getInt("retention_days"), 1));
            return request;
        });
    }

    public void updateBackupSettings(BackupSettingsRequest request) {
        String directory = normalizeBackupDirectory(request.getBackupDirectory());
        String frequency = normalizeFrequency(request.getScheduleFrequency());
        String scheduleTime = normalizeScheduleTime(request.getScheduleTime());
        int retentionDays = (int) clamp(request.getRetentionDays(), 1, 3650);

        jdbcTemplate.update("""
                UPDATE system_backup_config
                SET backup_directory = ?,
                    auto_backup_enabled = ?,
                    schedule_frequency = ?,
                    schedule_time = ?,
                    retention_days = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE config_key = 'default'
                """, directory, request.isAutoBackupEnabled(), frequency, scheduleTime, retentionDays);
    }

    public SecuritySettingsRequest getSecuritySettings() {
        return jdbcTemplate.queryForObject("""
                SELECT session_control_enabled,
                       max_concurrent_sessions,
                       session_timeout_minutes,
                       prevent_new_login,
                       row_level_security_enabled
                FROM system_security_policy
                WHERE policy_key = 'default'
                """, (rs, rowNum) -> {
            SecuritySettingsRequest request = new SecuritySettingsRequest();
            request.setSessionControlEnabled(rs.getBoolean("session_control_enabled"));
            request.setMaxConcurrentSessions(Math.max(rs.getInt("max_concurrent_sessions"), 0));
            request.setSessionTimeoutMinutes(Math.max(rs.getInt("session_timeout_minutes"), 1));
            request.setPreventNewLogin(rs.getBoolean("prevent_new_login"));
            request.setRowLevelSecurityEnabled(rs.getBoolean("row_level_security_enabled"));
            return request;
        });
    }

    public void updateSecuritySettings(SecuritySettingsRequest request) {
        jdbcTemplate.update("""
                UPDATE system_security_policy
                SET session_control_enabled = ?,
                    max_concurrent_sessions = ?,
                    session_timeout_minutes = ?,
                    prevent_new_login = ?,
                    row_level_security_enabled = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE policy_key = 'default'
                """,
                request.isSessionControlEnabled(),
                (int) clamp(request.getMaxConcurrentSessions(), 0, 10),
                (int) clamp(request.getSessionTimeoutMinutes(), 1, 240),
                request.isPreventNewLogin(),
                request.isRowLevelSecurityEnabled()
        );
    }

    public List<BackupHistoryDto> getRecentBackups() {
        return jdbcTemplate.query("""
                SELECT backup_id,
                       trigger_type,
                       status,
                       file_name,
                       file_path,
                       file_size_bytes,
                       triggered_by,
                       started_at,
                       completed_at,
                       message
                FROM system_backup_history
                ORDER BY started_at DESC
                LIMIT 12
                """, (rs, rowNum) -> {
            BackupHistoryDto dto = new BackupHistoryDto();
            dto.setBackupId(rs.getLong("backup_id"));
            dto.setTriggerType(rs.getString("trigger_type"));
            dto.setStatus(rs.getString("status"));
            dto.setFileName(rs.getString("file_name"));
            dto.setFilePath(rs.getString("file_path"));
            dto.setFileSizeBytes((Long) rs.getObject("file_size_bytes"));
            dto.setTriggeredBy(rs.getString("triggered_by"));
            dto.setStartedAt(rs.getTimestamp("started_at"));
            dto.setCompletedAt(rs.getTimestamp("completed_at"));
            dto.setMessage(rs.getString("message"));
            return dto;
        });
    }

    public BackupHistoryDto runManualBackup(String actor) {
        return createBackup("MANUAL", actor);
    }

    public Path getBackupFilePath(Long backupId) {
        String filePath = jdbcTemplate.queryForObject("""
                SELECT file_path
                FROM system_backup_history
                WHERE backup_id = ?
                  AND status = 'SUCCESS'
                """, String.class, backupId);

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Backup file is not available.");
        }

        return Path.of(filePath).toAbsolutePath().normalize();
    }

    public String getPreferenceCss() {
        SystemUiSettingsRequest settings = getUiSettings();
        double scale = clamp(settings.getFontScale(), 0.9, 1.25);
        String accent = sanitizeColor(settings.getAccentColor());
        String fontScale = String.format(Locale.US, "%.2f", scale);

        return """
                :root {
                    --system-font-scale: %s;
                    --system-accent-color: %s;
                    --primary-start: %s;
                    --primary-end: color-mix(in srgb, %s 68%%, white);
                }

                body .dashboard-app {
                    font-size: calc(14px * var(--system-font-scale));
                }

                body .app-title,
                body .sidebar-title,
                body .nav-title {
                    font-size: calc(1em * var(--system-font-scale));
                }

                body .page-intro h1,
                body .hero-card h1 {
                    font-size: calc(34px * var(--system-font-scale));
                }

                body .section-title h2,
                body .module-card h2 {
                    font-size: calc(22px * var(--system-font-scale));
                }

                body .submit-btn,
                body .login-btn,
                body .action-button.primary {
                    background: linear-gradient(90deg, var(--system-accent-color) 0%%, color-mix(in srgb, var(--system-accent-color) 72%%, white) 100%%);
                }

                body .module-card a,
                body .text-btn,
                body .nav-menu a.active .nav-title,
                body .nav-submenu a.active .nav-title {
                    color: var(--system-accent-color);
                }

                body .nav-menu a.active,
                body .nav-submenu a.active {
                    border-left-color: var(--system-accent-color);
                }
                """.formatted(fontScale, accent, accent, accent);
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    public void runScheduledBackupIfDue() {
        BackupSettingsRequest settings = getBackupSettings();
        if (!settings.isAutoBackupEnabled() || !isScheduledBackupDue(settings)) {
            return;
        }

        try {
            createBackup("AUTO", "system");
        } catch (RuntimeException ignored) {
            // The failure is recorded in system_backup_history; keep the scheduler alive.
        }
    }

    private boolean isScheduledBackupDue(BackupSettingsRequest settings) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime scheduleTime = LocalTime.parse(settings.getScheduleTime());
        if (now.toLocalTime().isBefore(scheduleTime)) {
            return false;
        }

        Timestamp lastBackup = jdbcTemplate.queryForObject("""
                SELECT last_backup_at
                FROM system_backup_config
                WHERE config_key = 'default'
                """, Timestamp.class);

        if (lastBackup == null) {
            return true;
        }

        LocalDateTime last = lastBackup.toLocalDateTime();
        if ("WEEKLY".equals(settings.getScheduleFrequency())) {
            return last.isBefore(now.minusDays(7));
        }

        return last.toLocalDate().isBefore(LocalDate.now());
    }

    private BackupHistoryDto createBackup(String triggerType, String actor) {
        if (!backupRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Another backup is already running.");
        }

        Long backupId = null;
        try {
            backupId = createBackupHistoryRow(triggerType, actor);
            BackupSettingsRequest settings = getBackupSettings();
            Path directory = Path.of(settings.getBackupDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(directory);

            String fileName = "blood_inventory_db_%s_%s.sql".formatted(
                    LocalDateTime.now().format(BACKUP_TIMESTAMP),
                    triggerType.toLowerCase(Locale.ROOT)
            );
            Path backupPath = directory.resolve(fileName).normalize();
            if (!backupPath.startsWith(directory)) {
                throw new IllegalArgumentException("Backup path is outside the configured directory.");
            }

            writeSqlBackup(backupPath);
            long fileSize = Files.size(backupPath);

            jdbcTemplate.update("""
                    UPDATE system_backup_history
                    SET status = 'SUCCESS',
                        file_name = ?,
                        file_path = ?,
                        file_size_bytes = ?,
                        completed_at = CURRENT_TIMESTAMP,
                        message = ?
                    WHERE backup_id = ?
                    """, fileName, backupPath.toString(), fileSize, "Backup completed successfully.", backupId);

            jdbcTemplate.update("""
                    UPDATE system_backup_config
                    SET last_backup_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE config_key = 'default'
                    """);

            cleanupExpiredBackups(settings.getRetentionDays());
            return getBackupById(backupId);
        } catch (Exception ex) {
            if (backupId != null) {
                jdbcTemplate.update("""
                        UPDATE system_backup_history
                        SET status = 'FAILED',
                            completed_at = CURRENT_TIMESTAMP,
                            message = ?
                        WHERE backup_id = ?
                        """, truncate(ex.getMessage(), 680), backupId);
            }
            throw new RuntimeException("Backup failed: " + ex.getMessage(), ex);
        } finally {
            backupRunning.set(false);
        }
    }

    private Long createBackupHistoryRow(String triggerType, String actor) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO system_backup_history (
                    trigger_type,
                    status,
                    triggered_by,
                    started_at,
                    message
                )
                VALUES (?, 'RUNNING', ?, CURRENT_TIMESTAMP, 'Backup started.')
                RETURNING backup_id
                """, Long.class, triggerType, actor);
    }

    private BackupHistoryDto getBackupById(Long backupId) {
        return jdbcTemplate.queryForObject("""
                SELECT backup_id,
                       trigger_type,
                       status,
                       file_name,
                       file_path,
                       file_size_bytes,
                       triggered_by,
                       started_at,
                       completed_at,
                       message
                FROM system_backup_history
                WHERE backup_id = ?
                """, (rs, rowNum) -> {
            BackupHistoryDto dto = new BackupHistoryDto();
            dto.setBackupId(rs.getLong("backup_id"));
            dto.setTriggerType(rs.getString("trigger_type"));
            dto.setStatus(rs.getString("status"));
            dto.setFileName(rs.getString("file_name"));
            dto.setFilePath(rs.getString("file_path"));
            dto.setFileSizeBytes((Long) rs.getObject("file_size_bytes"));
            dto.setTriggeredBy(rs.getString("triggered_by"));
            dto.setStartedAt(rs.getTimestamp("started_at"));
            dto.setCompletedAt(rs.getTimestamp("completed_at"));
            dto.setMessage(rs.getString("message"));
            return dto;
        }, backupId);
    }

    private void writeSqlBackup(Path backupPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(backupPath, StandardCharsets.UTF_8)) {
            writer.write("-- Blood Inventory Management System backup\n");
            writer.write("-- Generated at: " + Timestamp.valueOf(LocalDateTime.now()) + "\n\n");
            writer.write("BEGIN;\n\n");

            List<String> tableNames = jdbcTemplate.queryForList("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_type = 'BASE TABLE'
                    ORDER BY table_name
                    """, String.class);

            for (String tableName : tableNames) {
                writeTableData(writer, tableName);
            }

            writer.write("\nCOMMIT;\n");
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private void writeTableData(BufferedWriter writer, String tableName) throws IOException {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                ORDER BY ordinal_position
                """, String.class, tableName);

        if (columns.isEmpty()) {
            return;
        }

        writer.write("-- Data for table public." + tableName + "\n");
        String quotedTable = quoteIdentifier("public") + "." + quoteIdentifier(tableName);
        String quotedColumns = columns.stream()
                .map(this::quoteIdentifier)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        String selectSql = "SELECT " + quotedColumns + " FROM " + quotedTable;

        jdbcTemplate.query(selectSql, rs -> {
            try {
                writer.write("INSERT INTO ");
                writer.write(quotedTable);
                writer.write(" (");
                writer.write(quotedColumns);
                writer.write(") VALUES (");
                for (int index = 0; index < columns.size(); index++) {
                    if (index > 0) {
                        writer.write(", ");
                    }
                    writer.write(sqlLiteral(rs.getObject(columns.get(index))));
                }
                writer.write(");\n");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });

        writer.write("\n");
    }

    private void cleanupExpiredBackups(int retentionDays) {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusDays(retentionDays));
        List<BackupHistoryDto> expired = jdbcTemplate.query("""
                SELECT backup_id,
                       trigger_type,
                       status,
                       file_name,
                       file_path,
                       file_size_bytes,
                       triggered_by,
                       started_at,
                       completed_at,
                       message
                FROM system_backup_history
                WHERE status = 'SUCCESS'
                  AND completed_at < ?
                """, (rs, rowNum) -> {
            BackupHistoryDto dto = new BackupHistoryDto();
            dto.setBackupId(rs.getLong("backup_id"));
            dto.setFilePath(rs.getString("file_path"));
            return dto;
        }, cutoff);

        for (BackupHistoryDto backup : expired) {
            try {
                if (backup.getFilePath() != null) {
                    Files.deleteIfExists(Path.of(backup.getFilePath()));
                }
                jdbcTemplate.update("""
                        UPDATE system_backup_history
                        SET status = 'EXPIRED',
                            message = 'Backup expired by retention policy.'
                        WHERE backup_id = ?
                        """, backup.getBackupId());
            } catch (IOException ignored) {
                jdbcTemplate.update("""
                        UPDATE system_backup_history
                        SET message = 'Retention cleanup could not delete the backup file.'
                        WHERE backup_id = ?
                        """, backup.getBackupId());
            }
        }
    }

    private String settingValue(String settingKey, String fallback) {
        List<String> values = jdbcTemplate.queryForList("""
                SELECT setting_value
                FROM system_setting
                WHERE setting_key = ?
                """, String.class, settingKey);
        return values.isEmpty() ? fallback : values.get(0);
    }

    private void upsertSetting(String settingKey, String settingValue) {
        jdbcTemplate.update("""
                INSERT INTO system_setting (setting_key, setting_value, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (setting_key)
                DO UPDATE SET setting_value = EXCLUDED.setting_value,
                              updated_at = CURRENT_TIMESTAMP
                """, settingKey, settingValue);
    }

    private String normalizeBackupDirectory(String directory) {
        String normalized = directory == null ? "" : directory.trim();
        return normalized.isBlank() ? "backups" : normalized;
    }

    private String normalizeFrequency(String frequency) {
        String normalized = frequency == null ? "DAILY" : frequency.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_FREQUENCIES.contains(normalized) ? normalized : "DAILY";
    }

    private String normalizeScheduleTime(String scheduleTime) {
        String normalized = scheduleTime == null ? "23:00" : scheduleTime.trim();
        if (!SCHEDULE_TIME.matcher(normalized).matches()) {
            return "23:00";
        }

        try {
            LocalTime.parse(normalized);
            return normalized;
        } catch (DateTimeParseException ex) {
            return "23:00";
        }
    }

    private String sanitizeLanguage(String languageCode) {
        String normalized = languageCode == null ? "en" : languageCode.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_LANGUAGES.containsKey(normalized) ? normalized : "en";
    }

    private String sanitizeColor(String color) {
        String normalized = color == null ? "" : color.trim();
        return HEX_COLOR.matcher(normalized).matches() ? normalized : "#2f80ed";
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String sqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof BigDecimal) {
            return value.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "TRUE" : "FALSE";
        }
        if (value instanceof byte[] bytes) {
            return "'\\\\x" + HexFormat.of().formatHex(bytes) + "'";
        }
        if (value instanceof Timestamp || value instanceof Date || value instanceof Time) {
            return "'" + value + "'";
        }

        return "'" + value.toString().replace("'", "''") + "'";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "Unknown backup error.";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }
}
