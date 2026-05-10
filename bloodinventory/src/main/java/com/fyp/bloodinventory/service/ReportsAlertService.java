package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.AvailableStockDto;
import com.fyp.bloodinventory.dto.NearExpiryComponentDto;
import com.fyp.bloodinventory.dto.ReportsSummaryDto;
import com.fyp.bloodinventory.dto.StaffRoleTotalDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportsAlertService {

    private final JdbcTemplate jdbcTemplate;

    public ReportsAlertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReportsSummaryDto getReportsSummary() {
        String sql = "SELECT * FROM fn_reports_summary()";

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            ReportsSummaryDto dto = new ReportsSummaryDto();
            dto.setTotalStaff(rs.getLong("total_staff"));
            dto.setTotalDonors(rs.getLong("total_donors"));
            dto.setTotalDonations(rs.getLong("total_donations"));
            dto.setTotalComponents(rs.getLong("total_components"));
            dto.setAvailableComponents(rs.getLong("available_components"));
            dto.setNearExpiryComponents(rs.getLong("near_expiry_components"));
            return dto;
        });
    }

    public List<AvailableStockDto> getAvailableStockByType() {
        String sql = "SELECT * FROM fn_available_stock_by_type()";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AvailableStockDto dto = new AvailableStockDto();
            dto.setComponentType(rs.getString("component_type"));
            dto.setTotalAvailable(rs.getLong("total_available"));
            return dto;
        });
    }

    public List<NearExpiryComponentDto> getNearExpiryAlerts() {
        String sql = "SELECT * FROM fn_report_near_expiry_alerts()";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            NearExpiryComponentDto dto = new NearExpiryComponentDto();
            dto.setComponentId(rs.getLong("component_id"));
            dto.setComponentType(rs.getString("component_type"));
            dto.setStatus(rs.getString("status"));
            dto.setExpiryTimestamp(rs.getTimestamp("expiry_timestamp"));
            dto.setLocationId(rs.getLong("location_id"));
            return dto;
        });
    }

    public List<StaffRoleTotalDto> getStaffTotalsByRole() {
        String sql = "SELECT * FROM fn_staff_totals_by_role()";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            StaffRoleTotalDto dto = new StaffRoleTotalDto();
            dto.setStaffType(rs.getString("staff_type"));
            dto.setTotalStaff(rs.getLong("total_staff"));
            return dto;
        });
    }
}