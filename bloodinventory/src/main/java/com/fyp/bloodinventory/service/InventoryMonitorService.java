package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.InventoryExpiryChartDto;
import com.fyp.bloodinventory.dto.InventoryStatusDto;
import com.fyp.bloodinventory.dto.InventorySummaryDto;
import com.fyp.bloodinventory.dto.NearExpiryComponentDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryMonitorService {

    private final JdbcTemplate jdbcTemplate;

    public InventoryMonitorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public InventorySummaryDto getInventorySummary() {
        String sql = "SELECT * FROM fn_inventory_summary()";

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            InventorySummaryDto dto = new InventorySummaryDto();
            dto.setTotalComponents(rs.getLong("total_components"));
            dto.setAvailableComponents(rs.getLong("available_components"));
            dto.setNearExpiryComponents(rs.getLong("near_expiry_components"));
            dto.setTotalDonations(rs.getLong("total_donations"));
            return dto;
        });
    }

    public List<InventoryStatusDto> getComponentStatusSummary() {
        String sql = "SELECT * FROM fn_inventory_component_status()";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            InventoryStatusDto dto = new InventoryStatusDto();
            dto.setComponentType(rs.getString("component_type"));
            dto.setStatus(rs.getString("status"));
            dto.setTotalUnits(rs.getLong("total_units"));
            return dto;
        });
    }

    public List<InventoryExpiryChartDto> getExpiryChartData() {
        String sql = """
                SELECT
                    expiry_timestamp::DATE AS expiry_date,
                    UPPER(status) AS status,
                    COUNT(*)::BIGINT AS total_units
                FROM blood_component
                GROUP BY expiry_timestamp::DATE, UPPER(status)
                ORDER BY expiry_date ASC, status ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            InventoryExpiryChartDto dto = new InventoryExpiryChartDto();
            dto.setExpiryDate(rs.getDate("expiry_date").toLocalDate());
            dto.setStatus(rs.getString("status"));
            dto.setTotalUnits(rs.getLong("total_units"));
            return dto;
        });
    }

    public List<NearExpiryComponentDto> getNearExpiryComponents() {
        String sql = "SELECT * FROM fn_near_expiry_components()";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            NearExpiryComponentDto dto = new NearExpiryComponentDto();
            dto.setComponentId(rs.getLong("component_id"));
            dto.setComponentType(rs.getString("component_type"));
            dto.setExpiryTimestamp(rs.getTimestamp("expiry_timestamp"));
            dto.setStatus(rs.getString("status"));
            dto.setDonationId(rs.getLong("donation_id"));
            dto.setLocationId(rs.getLong("location_id"));
            return dto;
        });
    }
}
