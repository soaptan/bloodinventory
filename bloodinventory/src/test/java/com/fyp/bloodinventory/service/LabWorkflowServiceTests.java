package com.fyp.bloodinventory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"all", "null"})
class LabWorkflowServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseAuditContextService auditContextService = mock(DatabaseAuditContextService.class);
    private final LabWorkflowService service = new LabWorkflowService(jdbcTemplate, auditContextService);

    @Test
    @SuppressWarnings("null")
    void availableComponentStatusAcceptsPassedLabResult() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(180L))).thenReturn(1L);
        when(jdbcTemplate.update(anyString(), eq("AVAILABLE"), eq(180L))).thenReturn(1);

        service.updateComponentStatus(180L, "AVAILABLE");

        ArgumentCaptor<String> safeResultSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(safeResultSql.capture(), eq(Long.class), eq(180L));
        assertThat(safeResultSql.getValue()).contains("'PASSED'");
        verify(jdbcTemplate).update(anyString(), eq("AVAILABLE"), eq(180L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentMonthTrendSynchronizesPendingAndCompletedOnOneCalendar() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        service.getCurrentMonthTrend();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("generate_series")
                .contains("date_trunc('month', CURRENT_DATE)")
                .contains("pending_by_day")
                .contains("completed_by_day")
                .contains("pending_by_day.trend_date = calendar.trend_date")
                .contains("completed_by_day.trend_date = calendar.trend_date")
                .contains("ORDER BY calendar.trend_date ASC");
    }

    @Test
    @SuppressWarnings("null")
    void discardedComponentStatusMarksLatestLabResultFailed() {
        when(jdbcTemplate.update(anyString(), eq("DISCARDED"), eq(36L))).thenReturn(1);
        when(jdbcTemplate.update(anyString(), eq("FAILED"), eq(36L))).thenReturn(1);

        service.updateComponentStatus(36L, "DISCARDED");

        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(updateSql.capture(), anyString(), eq(36L));
        assertThat(updateSql.getAllValues().get(1))
                .contains("UPDATE lab_test")
                .contains("final_status = ?");
    }
}
