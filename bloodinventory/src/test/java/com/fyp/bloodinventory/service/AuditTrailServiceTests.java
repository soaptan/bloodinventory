package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.AuditTrailDto;
import com.fyp.bloodinventory.dto.AuditTrailSummaryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"all", "null"})
class AuditTrailServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditTrailService service = new AuditTrailService(jdbcTemplate);

    @Test
    void auditRecordsShowLatestInsertUpdateDeleteOnly() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.getAuditRecords(null, null, null, null, null, "newest", 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
                .contains("LEFT JOIN staff actor ON actor.staff_id = at.user_id")
                .contains("COALESCE(NULLIF(at.username, ''), NULLIF(actor.username, '')) AS username")
                .contains("COALESCE(NULLIF(at.role, ''), actor.staff_type::TEXT) AS role")
                .contains("UPPER(at.operation_type) IN ('INSERT', 'UPDATE', 'DELETE')")
                .contains("LOWER(COALESCE(at.table_name, '')) <> 'staff_login_session'")
                .contains("ORDER BY at.event_timestamp DESC, at.audit_id DESC")
                .contains("LIMIT ?");
    }

    @Test
    void auditSummaryExcludesSessionHeartbeatRows() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class))).thenReturn(new AuditTrailSummaryDto());

        service.getSummary();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("LEFT JOIN staff actor ON actor.staff_id = at.user_id")
                .contains("COUNT(DISTINCT COALESCE(COALESCE(NULLIF(at.username, ''), NULLIF(actor.username, '')), at.user_id::TEXT))")
                .contains("UPPER(at.operation_type) IN ('INSERT', 'UPDATE', 'DELETE')")
                .contains("LOWER(COALESCE(at.table_name, '')) <> 'staff_login_session'");
    }

    @Test
    void auditSearchBindsOnlySearchTermsAndLimit() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.<AuditTrailDto>of());

        service.getAuditRecords("deferral", null, null, null, null, "newest", 20);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), params.capture());
        assertThat(params.getValue()).hasSize(14);
        assertThat(params.getValue()).allSatisfy(value ->
                assertThat(value).isIn("%deferral%", 20)
        );
        assertThat(params.getValue()[13]).isEqualTo(20);
    }

    @Test
    void operationFiltersAreMutationTypes() {
        assertThat(service.getOperationTypes()).containsExactly("INSERT", "UPDATE", "DELETE");
    }

    @Test
    void tableFilterOptionsExcludeSessionHeartbeatRows() {
        when(jdbcTemplate.queryForList(anyString(), any(Class.class))).thenReturn(List.of());

        service.getTableNames();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), any(Class.class));
        assertThat(sql.getValue())
                .contains("UPPER(at.operation_type) IN ('INSERT', 'UPDATE', 'DELETE')")
                .contains("LOWER(COALESCE(at.table_name, '')) <> 'staff_login_session'");
    }
}
