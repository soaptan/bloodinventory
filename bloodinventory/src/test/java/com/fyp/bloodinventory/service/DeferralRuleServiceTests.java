package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.DeferralRuleRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"all", "null"})
class DeferralRuleServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseAuditContextService auditContextService = mock(DatabaseAuditContextService.class);
    private final DeferralRuleService service = new DeferralRuleService(jdbcTemplate, auditContextService);

    @Test
    void permanentRuleCreationIgnoresCoolingPeriod() {
        DeferralRuleRequest request = request("Permanent reason", 180, "PERMANENT", 41L);

        service.addRule(request);

        verify(jdbcTemplate).update(
                eq("CALL sp_add_deferral_rule(?, ?, ?, ?)"),
                eq("Permanent reason"),
                eq(0),
                eq(41L),
                eq("PERMANENT")
        );
    }

    @Test
    void permanentRuleUpdateClearsCoolingPeriod() {
        DeferralRuleRequest request = request("Permanent update", 90, "PERMANENT", 41L);
        when(jdbcTemplate.update(anyString(), eq("Permanent update"), eq(0), eq("PERMANENT"), eq(41L), eq(7L)))
                .thenReturn(1);

        service.updateRule(7L, request);

        verify(jdbcTemplate).update(
                anyString(),
                eq("Permanent update"),
                eq(0),
                eq("PERMANENT"),
                eq(41L),
                eq(7L)
        );
    }

    @Test
    void temporaryRuleKeepsCoolingPeriod() {
        DeferralRuleRequest request = request("Temporary reason", 14, "TEMPORARY", 41L);

        service.addRule(request);

        verify(jdbcTemplate).update(
                eq("CALL sp_add_deferral_rule(?, ?, ?, ?)"),
                eq("Temporary reason"),
                eq(14),
                eq(41L),
                eq("TEMPORARY")
        );
    }

    @Test
    void addRuleChecksForAnExistingNameUsingTrimmedCaseInsensitiveComparison() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("Recent Fever")))
                .thenReturn(false);

        service.addRule(request("  Recent Fever  ", 14, "TEMPORARY", 41L));

        verify(jdbcTemplate).queryForObject(
                eq("SELECT EXISTS (SELECT 1 FROM deferral_reason WHERE LOWER(BTRIM(description)) = LOWER(BTRIM(?)))"),
                eq(Boolean.class),
                eq("Recent Fever")
        );
    }

    @Test
    void addRuleRejectsAnExistingNameBeforeCallingTheDatabaseProcedure() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("recent fever")))
                .thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.addRule(request(" recent fever ", 14, "TEMPORARY", 41L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Deferral rule name already exists.");

        verify(jdbcTemplate, never()).update(
                eq("CALL sp_add_deferral_rule(?, ?, ?, ?)"),
                eq("Recent Fever"),
                eq(14),
                eq(41L),
                eq("TEMPORARY")
        );
    }

    @Test
    void addRuleConvertsDatabaseDuplicateIntoTheSameValidationMessage() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("Recent Fever")))
                .thenReturn(false);
        doThrow(new DataIntegrityViolationException("ux_deferral_reason_description_normalized"))
                .when(jdbcTemplate)
                .update(
                        eq("CALL sp_add_deferral_rule(?, ?, ?, ?)"),
                        eq("Recent Fever"),
                        eq(14),
                        eq(41L),
                        eq("TEMPORARY")
                );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.addRule(request("Recent Fever", 14, "TEMPORARY", 41L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Deferral rule name already exists.");
    }

    private DeferralRuleRequest request(String description, Integer coolingDays, String lockType, Long staffId) {
        DeferralRuleRequest request = new DeferralRuleRequest();
        request.setDescription(description);
        request.setDefaultCoolingPeriodDays(coolingDays);
        request.setLockType(lockType);
        request.setStaffId(staffId);
        return request;
    }
}
