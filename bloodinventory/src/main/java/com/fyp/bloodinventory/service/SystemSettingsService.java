package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.BackupHistoryDto;
import com.fyp.bloodinventory.dto.BackupSettingsRequest;
import com.fyp.bloodinventory.dto.LanguageSettingsRequest;
import com.fyp.bloodinventory.dto.SecuritySettingsRequest;
import com.fyp.bloodinventory.dto.SystemUiSettingsRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @Transactional
    public void updateUiSettings(SystemUiSettingsRequest request) {
        persistUiSettings(request);
    }

    private void persistUiSettings(SystemUiSettingsRequest request) {
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
        return Objects.requireNonNull(jdbcTemplate.queryForObject("""
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
        }), "Backup settings must not be null.");
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
        return Objects.requireNonNull(jdbcTemplate.queryForObject("""
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
        }), "Security settings must not be null.");
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

    public BackupHistoryDto recoverBackup(Long backupId, String actor) {
        if (backupId == null) {
            throw new IllegalArgumentException("Select a backup before starting recovery.");
        }
        if (!backupRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Another backup or recovery is already running.");
        }

        try {
            BackupHistoryDto sourceBackup = getBackupById(backupId);
            if (!"SUCCESS".equalsIgnoreCase(sourceBackup.getStatus())) {
                throw new IllegalArgumentException("Only successful backups can be recovered.");
            }

            Path backupPath = resolveAvailableBackupPath(sourceBackup);
            BackupHistoryDto recoveryPoint = createBackupUnlocked("RECOVERY_POINT", actor, false);

            try {
                restoreDatabaseFromSqlBackup(backupPath);
                upsertBackupHistorySnapshot(sourceBackup);
                upsertBackupHistorySnapshot(recoveryPoint);
                syncIdentitySequences();

                String message = "Database recovered from backup #%d. Safety backup: %s.".formatted(
                        sourceBackup.getBackupId(),
                        recoveryPoint.getFileName()
                );
                return createRecoveryHistoryRow(sourceBackup, recoveryPoint, actor, "SUCCESS", message);
            } catch (Exception ex) {
                String message = "Recovery failed for backup #%d. Safety backup: %s. %s".formatted(
                        sourceBackup.getBackupId(),
                        recoveryPoint.getFileName(),
                        rootCauseMessage(ex)
                );
                createRecoveryHistoryRow(sourceBackup, recoveryPoint, actor, "FAILED", message);
                throw new RuntimeException("Recovery failed: " + rootCauseMessage(ex), ex);
            }
        } finally {
            backupRunning.set(false);
        }
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
                    --color-primary: %s;
                    --color-primary-dark: color-mix(in srgb, %s 74%%, black);
                    --color-primary-light: color-mix(in srgb, %s 18%%, white);
                }

                body .dashboard-app {
                    font-size: calc(14px * var(--system-font-scale));
                }

                body .app-header {
                    font-family: "Segoe UI", Tahoma, Geneva, Verdana, sans-serif;
                    text-rendering: optimizeLegibility;
                    -webkit-font-smoothing: antialiased;
                }

                body .app-title {
                    font-size: clamp(16px, calc(18px * var(--system-font-scale)), 23px);
                    font-weight: 800;
                    line-height: 1.2;
                    letter-spacing: -0.01em;
                }

                body .app-search-input,
                body .profile-trigger-label {
                    font-size: clamp(14px, calc(14px * var(--system-font-scale)), 18px);
                    line-height: 1.4;
                }

                body .sidebar-title {
                    font-size: calc(18px * var(--system-font-scale));
                }

                body .nav-title {
                    font-size: calc(15px * var(--system-font-scale));
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
                body .action-button.primary,
                body .btn-primary,
                body .primary-btn {
                    background: linear-gradient(90deg, var(--system-accent-color) 0%%, color-mix(in srgb, var(--system-accent-color) 72%%, white) 100%%);
                    border-color: var(--system-accent-color);
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
                    background: color-mix(in srgb, var(--system-accent-color) 12%%, transparent);
                }

                body input:focus,
                body select:focus,
                body textarea:focus {
                    border-color: var(--system-accent-color);
                    box-shadow: 0 0 0 3px color-mix(in srgb, var(--system-accent-color) 18%%, transparent);
                }

                body.theme-dark .app-header {
                    border-bottom-color: color-mix(in srgb, var(--system-accent-color) 55%%, #2a3342);
                }
                """.formatted(fontScale, accent, accent, accent, accent, accent, accent);
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
            throw new IllegalStateException("Another backup or recovery is already running.");
        }

        try {
            return createBackupUnlocked(triggerType, actor, true);
        } finally {
            backupRunning.set(false);
        }
    }

    private BackupHistoryDto createBackupUnlocked(String triggerType, String actor, boolean cleanupExpiredAfterBackup) {
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

            if (cleanupExpiredAfterBackup) {
                cleanupExpiredBackups(settings.getRetentionDays());
            }
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
        }
    }

    private Long createBackupHistoryRow(String triggerType, String actor) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject("""
                INSERT INTO system_backup_history (
                    trigger_type,
                    status,
                    triggered_by,
                    started_at,
                    message
                )
                VALUES (?, 'RUNNING', ?, CURRENT_TIMESTAMP, 'Backup started.')
                RETURNING backup_id
                """, Long.class, triggerType, actor), "Backup ID must not be null.");
    }

    private BackupHistoryDto getBackupById(Long backupId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject("""
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
        }, backupId), "Backup history row must not be null.");
    }

    private Path resolveAvailableBackupPath(BackupHistoryDto backup) {
        if (backup.getFilePath() == null || backup.getFilePath().isBlank()) {
            throw new IllegalArgumentException("Backup file is not available.");
        }

        Path backupPath = Path.of(backup.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(backupPath) || !Files.isRegularFile(backupPath)) {
            throw new IllegalArgumentException("Backup file is missing from disk.");
        }

        return backupPath;
    }

    private void restoreDatabaseFromSqlBackup(Path backupPath) throws IOException {
        String recoveryScript = stripTransactionStatements(Files.readString(backupPath, StandardCharsets.UTF_8));
        ByteArrayResource scriptResource = new ByteArrayResource(recoveryScript.getBytes(StandardCharsets.UTF_8));
        EncodedResource encodedScript = new EncodedResource(scriptResource, StandardCharsets.UTF_8);

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureRestoreSchema(connection);
                List<String> tableNames = getPublicTableNames(connection);
                List<IdentityColumn> generatedAlwaysColumns = generatedAlwaysIdentityColumns(connection);
                setIdentityGeneration(connection, generatedAlwaysColumns, "BY DEFAULT");
                setUserTriggers(connection, tableNames, false);
                truncatePublicTables(connection, tableNames);
                ScriptUtils.executeSqlScript(connection, encodedScript);
                syncIdentitySequences(connection);
                setUserTriggers(connection, tableNames, true);
                setIdentityGeneration(connection, generatedAlwaysColumns, "ALWAYS");
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Could not restore SQL backup.", ex);
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
            return null;
        });
    }

    private String stripTransactionStatements(String script) {
        return script.replaceAll("(?im)^\\s*(BEGIN|COMMIT)\\s*;\\s*\\R?", "");
    }

    private void ensureRestoreSchema(Connection connection) throws SQLException {
        ensureLabTestTable(connection);
    }

    private void ensureLabTestTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS public.lab_test (
                        test_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
                        tti_screening character varying(30) NOT NULL,
                        blood_type_match character varying(20) NOT NULL,
                        final_status character varying(20) NOT NULL,
                        test_date date NOT NULL,
                        staff_id bigint NOT NULL,
                        donation_id bigint NOT NULL,
                        CONSTRAINT chk_labtest_blood_type_match CHECK (((blood_type_match)::text = ANY ((ARRAY['MATCHED'::character varying, 'NOT_MATCHED'::character varying, 'PENDING'::character varying])::text[]))),
                        CONSTRAINT chk_labtest_final_status CHECK (((final_status)::text = ANY ((ARRAY['PASSED'::character varying, 'FAILED'::character varying, 'QUARANTINED'::character varying])::text[])))
                    )
                    """);
        }

        addConstraintIfMissing(connection, "chk_labtest_blood_type_match",
                "ALTER TABLE public.lab_test ADD CONSTRAINT chk_labtest_blood_type_match CHECK (((blood_type_match)::text = ANY ((ARRAY['MATCHED'::character varying, 'NOT_MATCHED'::character varying, 'PENDING'::character varying])::text[])))");
        addConstraintIfMissing(connection, "chk_labtest_final_status",
                "ALTER TABLE public.lab_test ADD CONSTRAINT chk_labtest_final_status CHECK (((final_status)::text = ANY ((ARRAY['PASSED'::character varying, 'FAILED'::character varying, 'QUARANTINED'::character varying])::text[])))");
        addConstraintIfMissing(connection, "lab_test_pkey",
                "ALTER TABLE ONLY public.lab_test ADD CONSTRAINT lab_test_pkey PRIMARY KEY (test_id)");
        addConstraintIfMissing(connection, "lab_test_donation_id_key",
                "ALTER TABLE ONLY public.lab_test ADD CONSTRAINT lab_test_donation_id_key UNIQUE (donation_id)");
        addConstraintIfMissing(connection, "fk_labtest_donation",
                "ALTER TABLE ONLY public.lab_test ADD CONSTRAINT fk_labtest_donation FOREIGN KEY (donation_id) REFERENCES public.donation(donation_id) ON DELETE CASCADE");
        addConstraintIfMissing(connection, "fk_labtest_staff",
                "ALTER TABLE ONLY public.lab_test ADD CONSTRAINT fk_labtest_staff FOREIGN KEY (staff_id) REFERENCES public.lab_technician(staff_id) ON DELETE RESTRICT");

        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS idx_lab_test_staff_id ON public.lab_test USING btree (staff_id)");
        }
    }

    private void addConstraintIfMissing(Connection connection, String constraintName, String ddl) throws SQLException {
        if (constraintExists(connection, constraintName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private boolean constraintExists(Connection connection, String constraintName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_constraint
                    WHERE connamespace = 'public'::regnamespace
                      AND conname = ?
                )
                """)) {
            statement.setString(1, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private List<IdentityColumn> generatedAlwaysIdentityColumns(Connection connection) throws SQLException {
        List<IdentityColumn> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT table_schema,
                       table_name,
                       column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND is_identity = 'YES'
                  AND identity_generation = 'ALWAYS'
                ORDER BY table_name, ordinal_position
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                columns.add(new IdentityColumn(
                        resultSet.getString("table_schema"),
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name")
                ));
            }
        }
        return columns;
    }

    private void setIdentityGeneration(Connection connection,
                                       List<IdentityColumn> columns,
                                       String generation) throws SQLException {
        if (columns.isEmpty()) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            for (IdentityColumn column : columns) {
                statement.execute("ALTER TABLE %s.%s ALTER COLUMN %s SET GENERATED %s".formatted(
                        quoteIdentifier(column.schemaName()),
                        quoteIdentifier(column.tableName()),
                        quoteIdentifier(column.columnName()),
                        generation
                ));
            }
        }
    }

    private void setUserTriggers(Connection connection, List<String> tableNames, boolean enabled) throws SQLException {
        if (tableNames.isEmpty()) {
            return;
        }

        String triggerAction = enabled ? "ENABLE" : "DISABLE";
        try (Statement statement = connection.createStatement()) {
            for (String tableName : tableNames) {
                statement.execute("ALTER TABLE %s.%s %s TRIGGER USER".formatted(
                        quoteIdentifier("public"),
                        quoteIdentifier(tableName),
                        triggerAction
                ));
            }
        }
    }

    private void truncatePublicTables(Connection connection) throws SQLException {
        truncatePublicTables(connection, getPublicTableNames(connection));
    }

    private void truncatePublicTables(Connection connection, List<String> tableNames) throws SQLException {
        if (tableNames.isEmpty()) {
            return;
        }

        String qualifiedTables = tableNames.stream()
                .map(tableName -> quoteIdentifier("public") + "." + quoteIdentifier(tableName))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");

        try (Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE " + qualifiedTables + " RESTART IDENTITY CASCADE");
        }
    }

    private List<String> getPublicTableNames(Connection connection) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("table_name"));
            }
        }
        return tableNames;
    }

    private void upsertBackupHistorySnapshot(BackupHistoryDto backup) {
        if (backup == null || backup.getBackupId() == null) {
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO system_backup_history (
                    backup_id,
                    trigger_type,
                    status,
                    file_name,
                    file_path,
                    file_size_bytes,
                    triggered_by,
                    started_at,
                    completed_at,
                    message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (backup_id) DO UPDATE
                SET trigger_type = EXCLUDED.trigger_type,
                    status = EXCLUDED.status,
                    file_name = EXCLUDED.file_name,
                    file_path = EXCLUDED.file_path,
                    file_size_bytes = EXCLUDED.file_size_bytes,
                    triggered_by = EXCLUDED.triggered_by,
                    started_at = EXCLUDED.started_at,
                    completed_at = EXCLUDED.completed_at,
                    message = EXCLUDED.message
                """,
                backup.getBackupId(),
                backup.getTriggerType(),
                backup.getStatus(),
                backup.getFileName(),
                backup.getFilePath(),
                backup.getFileSizeBytes(),
                backup.getTriggeredBy(),
                backup.getStartedAt(),
                backup.getCompletedAt(),
                backup.getMessage()
        );
    }

    private BackupHistoryDto createRecoveryHistoryRow(BackupHistoryDto sourceBackup,
                                                      BackupHistoryDto recoveryPoint,
                                                      String actor,
                                                      String status,
                                                      String message) {
        Long recoveryId = Objects.requireNonNull(jdbcTemplate.queryForObject("""
                INSERT INTO system_backup_history (
                    trigger_type,
                    status,
                    file_name,
                    file_path,
                    file_size_bytes,
                    triggered_by,
                    started_at,
                    completed_at,
                    message
                )
                VALUES ('RECOVERY', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                RETURNING backup_id
                """, Long.class,
                status,
                sourceBackup.getFileName(),
                sourceBackup.getFilePath(),
                sourceBackup.getFileSizeBytes(),
                actor,
                truncate(message + " Recovery point: " + recoveryPoint.getFileName(), 680)
        ), "Recovery ID must not be null.");
        return getBackupById(recoveryId);
    }

    private void syncIdentitySequences() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            syncIdentitySequences(connection);
            return null;
        });
    }

    private void syncIdentitySequences(Connection connection) throws SQLException {
        try (PreparedStatement columnStatement = connection.prepareStatement("""
                SELECT table_schema,
                       table_name,
                       column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND (column_default LIKE 'nextval(%' OR is_identity = 'YES')
                ORDER BY table_name, ordinal_position
                """);
             ResultSet columns = columnStatement.executeQuery()) {
            while (columns.next()) {
                syncIdentitySequence(
                        connection,
                        columns.getString("table_schema"),
                        columns.getString("table_name"),
                        columns.getString("column_name")
                );
            }
        }
    }

    private void syncIdentitySequence(Connection connection,
                                      String schemaName,
                                      String tableName,
                                      String columnName) throws SQLException {
        String sequenceName;
        try (PreparedStatement sequenceStatement = connection.prepareStatement("SELECT pg_get_serial_sequence(?, ?)")) {
            sequenceStatement.setString(1, schemaName + "." + tableName);
            sequenceStatement.setString(2, columnName);
            try (ResultSet sequenceResult = sequenceStatement.executeQuery()) {
                if (!sequenceResult.next()) {
                    return;
                }
                sequenceName = sequenceResult.getString(1);
            }
        }

        if (sequenceName == null || sequenceName.isBlank()) {
            return;
        }

        long maxValue = 0;
        String maxSql = "SELECT MAX(%s) FROM %s.%s".formatted(
                quoteIdentifier(columnName),
                quoteIdentifier(schemaName),
                quoteIdentifier(tableName)
        );
        try (Statement maxStatement = connection.createStatement();
             ResultSet maxResult = maxStatement.executeQuery(maxSql)) {
            if (maxResult.next() && maxResult.getObject(1) instanceof Number number) {
                maxValue = number.longValue();
            }
        }

        try (PreparedStatement setvalStatement = connection.prepareStatement("SELECT setval(?::regclass, ?, ?)")) {
            setvalStatement.setString(1, sequenceName);
            setvalStatement.setLong(2, Math.max(maxValue, 1));
            setvalStatement.setBoolean(3, maxValue > 0);
            setvalStatement.execute();
        }
    }

    private void writeSqlBackup(Path backupPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(backupPath, StandardCharsets.UTF_8)) {
            writer.write("-- Blood Inventory Management System backup\n");
            writer.write("-- Generated at: " + Timestamp.valueOf(LocalDateTime.now()) + "\n\n");
            writer.write("BEGIN;\n\n");

            for (String tableName : backupTableNames()) {
                writeTableData(writer, tableName);
            }

            writer.write("\nCOMMIT;\n");
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private List<String> backupTableNames() {
        List<String> tableNames = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """, String.class);

        Map<String, Set<String>> dependenciesByTable = new HashMap<>();
        for (String tableName : tableNames) {
            dependenciesByTable.put(tableName, new LinkedHashSet<>());
        }

        jdbcTemplate.query("""
                SELECT tc.table_name,
                       ccu.table_name AS referenced_table
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.constraint_schema = tc.constraint_schema
                WHERE tc.table_schema = 'public'
                  AND ccu.table_schema = 'public'
                  AND tc.constraint_type = 'FOREIGN KEY'
                ORDER BY tc.table_name, ccu.table_name
                """, rs -> {
            String tableName = rs.getString("table_name");
            String referencedTable = rs.getString("referenced_table");
            if (!Objects.equals(tableName, referencedTable) && dependenciesByTable.containsKey(tableName)) {
                dependenciesByTable.get(tableName).add(referencedTable);
            }
        });

        List<String> orderedTables = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String tableName : tableNames) {
            visitBackupTable(tableName, dependenciesByTable, visited, visiting, orderedTables);
        }

        return orderedTables;
    }

    private void visitBackupTable(String tableName,
                                  Map<String, Set<String>> dependenciesByTable,
                                  Set<String> visited,
                                  Set<String> visiting,
                                  List<String> orderedTables) {
        if (visited.contains(tableName)) {
            return;
        }
        if (visiting.contains(tableName)) {
            return;
        }

        visiting.add(tableName);
        for (String dependency : dependenciesByTable.getOrDefault(tableName, Set.of())) {
            if (dependenciesByTable.containsKey(dependency)) {
                visitBackupTable(dependency, dependenciesByTable, visited, visiting, orderedTables);
            }
        }
        visiting.remove(tableName);
        visited.add(tableName);
        orderedTables.add(tableName);
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
        boolean hasIdentityColumn = hasIdentityColumn(tableName);
        String selectSql = "SELECT " + quotedColumns + " FROM " + quotedTable;

        jdbcTemplate.query(selectSql, rs -> {
            try {
                writer.write("INSERT INTO ");
                writer.write(quotedTable);
                writer.write(" (");
                writer.write(quotedColumns);
                writer.write(") ");
                if (hasIdentityColumn) {
                    writer.write("OVERRIDING SYSTEM VALUE ");
                }
                writer.write("VALUES (");
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

    private boolean hasIdentityColumn(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND is_identity = 'YES'
                )
                """, Boolean.class, tableName));
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

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        return truncate(current.getMessage(), 240);
    }

    private record IdentityColumn(String schemaName, String tableName, String columnName) {
    }
}
