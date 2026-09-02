package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.DeferralRuleDto;
import com.fyp.bloodinventory.dto.DeferralRuleRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class DeferralRuleService {

    private static final String TEMPORARY_LOCK = "TEMPORARY";
    private static final String PERMANENT_LOCK = "PERMANENT";
    private static final String DUPLICATE_NAME_MESSAGE = "Deferral rule name already exists.";
    private static final String DUPLICATE_NAME_CONSTRAINT = "ux_deferral_reason_description_normalized";

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseAuditContextService auditContextService;

    public DeferralRuleService(JdbcTemplate jdbcTemplate,
                               DatabaseAuditContextService auditContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditContextService = auditContextService;
    }

    public List<DeferralRuleDto> getAllRules() {
        String sql = "SELECT * FROM fn_get_deferral_rules()";

        return jdbcTemplate.query(sql, new RowMapper<DeferralRuleDto>() {
            @Override
            public DeferralRuleDto mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
                DeferralRuleDto dto = new DeferralRuleDto();
                dto.setReasonId(rs.getLong("reason_id"));
                dto.setDescription(rs.getString("description"));
                dto.setDefaultCoolingPeriodDays(rs.getInt("default_cooling_period_days"));
                dto.setLockType(rs.getString("lock_type"));
                dto.setStaffId(rs.getLong("staff_id"));
                dto.setActive(rs.getBoolean("is_active"));
                return dto;
            }
        });
    }

    public long countRules() {
        Long result = jdbcTemplate.queryForObject(
                "SELECT fn_count_deferral_rules()",
                Long.class
        );
        return result == null ? 0L : result;
    }

    @Transactional
    public void addRule(DeferralRuleRequest request) {
        String description = requireText(request.getDescription(), "Please enter the deferral reason.");
        String lockType = requireLockType(request.getLockType());
        Integer coolingDays = requireCoolingDays(request.getDefaultCoolingPeriodDays(), lockType);
        Long staffId = requireId(request.getStaffId(), "Signed-in administrator account was not found.");

        if (ruleNameExists(description)) {
            throw new RuntimeException(DUPLICATE_NAME_MESSAGE);
        }

        applyAuditContext();
        try {
            jdbcTemplate.update(
                    "CALL sp_add_deferral_rule(?, ?, ?, ?)",
                    description,
                    coolingDays,
                    staffId,
                    lockType
            );
        } catch (DataAccessException exception) {
            if (isDuplicateNameFailure(exception)) {
                throw new RuntimeException(DUPLICATE_NAME_MESSAGE, exception);
            }
            throw exception;
        }
    }

    @Transactional
    public void updateRule(Long reasonId, DeferralRuleRequest request) {
        applyAuditContext();
        Long requiredReasonId = requireId(reasonId, "Please select a deferral rule.");
        String description = requireText(request.getDescription(), "Please enter the deferral reason.");
        String lockType = requireLockType(request.getLockType());
        Integer coolingDays = requireCoolingDays(request.getDefaultCoolingPeriodDays(), lockType);
        Long staffId = requireId(request.getStaffId(), "Signed-in administrator account was not found.");

        int updatedRows = jdbcTemplate.update("""
                UPDATE deferral_reason
                SET description = ?,
                    default_cooling_period_days = ?,
                    lock_type = ?,
                    staff_id = ?
                WHERE reason_id = ?
                """, description, coolingDays, lockType, staffId, requiredReasonId);

        if (updatedRows == 0) {
            throw new RuntimeException("Deferral rule was not found.");
        }
    }

    @Transactional
    public void archiveRule(Long reasonId) {
        applyAuditContext();
        Long requiredReasonId = requireId(reasonId, "Please select a deferral rule.");

        int updatedRows = jdbcTemplate.update("""
                UPDATE deferral_reason
                SET is_active = FALSE
                WHERE reason_id = ?
                """, requiredReasonId);
        if (updatedRows == 0) {
            throw new RuntimeException("Deferral rule was not found.");
        }
    }

    @Transactional
    public void restoreRule(Long reasonId) {
        applyAuditContext();
        Long requiredReasonId = requireId(reasonId, "Please select a deferral rule.");

        int updatedRows = jdbcTemplate.update("""
                UPDATE deferral_reason
                SET is_active = TRUE
                WHERE reason_id = ?
                """, requiredReasonId);
        if (updatedRows == 0) {
            throw new RuntimeException("Deferral rule was not found.");
        }
    }

    private void applyAuditContext() {
        auditContextService.applyCurrentContext();
    }

    private boolean ruleNameExists(String description) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM deferral_reason WHERE LOWER(BTRIM(description)) = LOWER(BTRIM(?)))",
                Boolean.class,
                description
        );
        return Boolean.TRUE.equals(exists);
    }

    private boolean isDuplicateNameFailure(DataAccessException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && (message.contains(DUPLICATE_NAME_MESSAGE)
                    || message.contains(DUPLICATE_NAME_CONSTRAINT))) {
                return true;
            }
        }
        return false;
    }

    private Long requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new RuntimeException(message);
        }

        return value;
    }

    private Integer requireCoolingDays(Integer value, String lockType) {
        if (PERMANENT_LOCK.equals(lockType)) {
            return 0;
        }

        if (value == null || value < 0) {
            throw new RuntimeException("Please enter a valid cooling-off period.");
        }

        return value;
    }

    private String requireLockType(String value) {
        String normalized = requireText(value == null ? TEMPORARY_LOCK : value, "Please select a deferral lock type.")
                .toUpperCase();

        if (!TEMPORARY_LOCK.equals(normalized) && !PERMANENT_LOCK.equals(normalized)) {
            throw new RuntimeException("Please select a valid deferral lock type.");
        }

        return normalized;
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new RuntimeException(message);
        }

        return normalized;
    }
}
