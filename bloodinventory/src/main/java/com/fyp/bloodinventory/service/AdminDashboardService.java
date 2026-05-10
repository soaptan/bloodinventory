package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.AdminDashboardStats;
import com.fyp.bloodinventory.dto.DashboardOverviewTimelineDto;
import com.fyp.bloodinventory.dto.DashboardSummaryMetricDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public @NonNull AdminDashboardStats getDashboardStats() {
        AdminDashboardStats stats = new AdminDashboardStats();

        stats.setTotalStaff(getCount("SELECT COUNT(*) FROM staff"));
        stats.setTotalDonors(getCount("SELECT COUNT(*) FROM donor"));
        stats.setTotalDonations(getCount("SELECT COUNT(*) FROM donation"));
        stats.setTotalBloodComponents(getCount("SELECT COUNT(*) FROM blood_component"));
        stats.setAvailableComponents(getCount("SELECT COUNT(*) FROM blood_component WHERE status = 'AVAILABLE'"));
        stats.setNearExpiryComponents(
                getCount("SELECT COUNT(*) FROM blood_component " +
                         "WHERE expiry_timestamp <= CURRENT_TIMESTAMP + INTERVAL '3 days'")
        );

        return stats;
    }

    public @NonNull List<DashboardOverviewTimelineDto> getSystemOverviewTimeline(int days, String metric, String sort) {
        String sql = "SELECT * FROM fn_dashboard_system_overview(?, ?, ?)";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DashboardOverviewTimelineDto dto = new DashboardOverviewTimelineDto();
            dto.setActivityDate(rs.getDate("activity_date").toLocalDate());
            dto.setDonationCount(rs.getLong("donation_count"));
            dto.setComponentCount(rs.getLong("component_count"));
            dto.setAvailableCount(rs.getLong("available_count"));
            dto.setNearExpiryCount(rs.getLong("near_expiry_count"));
            dto.setSelectedTotal(rs.getLong("selected_total"));
            return dto;
        }, days, metric, sort);
    }

    public @NonNull List<DashboardSummaryMetricDto> getSummaryMetrics() {
        String sql = "SELECT * FROM fn_dashboard_summary_metrics()";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DashboardSummaryMetricDto dto = new DashboardSummaryMetricDto();
            dto.setMetricKey(rs.getString("metric_key"));
            dto.setMetricLabel(rs.getString("metric_label"));
            dto.setMetricValue(rs.getLong("metric_value"));
            dto.setMetricNote(rs.getString("metric_note"));
            dto.setMetricColor(rs.getString("metric_color"));
            return dto;
        });
    }

    private long getCount(@NonNull String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result.longValue();
    }
}
