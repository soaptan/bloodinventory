package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.DeferralRuleDto;
import com.fyp.bloodinventory.dto.DeferralRuleRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class DeferralRuleService {

    private final JdbcTemplate jdbcTemplate;

    public DeferralRuleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                dto.setStaffId(rs.getLong("staff_id"));
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

    public void addRule(DeferralRuleRequest request) {
        jdbcTemplate.update(
                "CALL sp_add_deferral_rule(?, ?, ?)",
                request.getDescription(),
                request.getDefaultCoolingPeriodDays(),
                request.getStaffId()
        );
    }
}
