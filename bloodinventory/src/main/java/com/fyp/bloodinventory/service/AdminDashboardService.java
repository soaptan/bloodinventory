package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.AdminDashboardStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminDashboardStats getDashboardStats() {
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

    private long getCount(@NonNull String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result.longValue();
    }
}
