package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.MedicalDeferralRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"all", "null"})
class MedicalWorkflowServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseAuditContextService auditContextService = mock(DatabaseAuditContextService.class);
    private final MedicalWorkflowService service = new MedicalWorkflowService(jdbcTemplate, auditContextService);

    @Test
    void permanentDeferralSetsPermanentLockWithoutExpiryDate() throws Exception {
        MedicalDeferralRequest request = deferralRequest(4L, 7L);
        when(jdbcTemplate.queryForObject(contains("FROM medical_staff"), eq(Long.class), eq("medical")))
                .thenReturn(12L);
        stubDeferralRule(7L, 0, "PERMANENT");

        service.recordDeferral(request, "medical");

        verify(jdbcTemplate).update(contains("UPDATE donor"), eq(true), isNull(), eq(4L));
    }

    @Test
    void temporaryDeferralSetsExpiryDateAndClearsPermanentLock() throws Exception {
        MedicalDeferralRequest request = deferralRequest(5L, 8L);
        when(jdbcTemplate.queryForObject(contains("FROM medical_staff"), eq(Long.class), eq("medical")))
                .thenReturn(12L);
        stubDeferralRule(8L, 14, "TEMPORARY");
        when(jdbcTemplate.queryForObject(contains("COALESCE(permanent_deferral"), eq(Boolean.class), eq(5L)))
                .thenReturn(false);

        service.recordDeferral(request, "medical");

        verify(jdbcTemplate).update(contains("UPDATE donor"), eq(false), ArgumentMatchers.any(Date.class), eq(5L));
    }

    private MedicalDeferralRequest deferralRequest(Long donorId, Long reasonId) {
        MedicalDeferralRequest request = new MedicalDeferralRequest();
        request.setDonorId(donorId);
        request.setReasonId(reasonId);
        return request;
    }

    private void stubDeferralRule(Long reasonId, int coolingDays, String lockType) throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), ArgumentMatchers.<RowMapper<?>>any(), eq(reasonId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getInt("default_cooling_period_days")).thenReturn(coolingDays);
                    when(rs.getString("lock_type")).thenReturn(lockType);
                    return mapper.mapRow(rs, 0);
                });
    }
}
