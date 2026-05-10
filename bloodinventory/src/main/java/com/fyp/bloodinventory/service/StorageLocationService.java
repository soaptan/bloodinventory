package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.StorageLocationDto;
import com.fyp.bloodinventory.dto.StorageLocationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class StorageLocationService {

    private final JdbcTemplate jdbcTemplate;

    public StorageLocationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StorageLocationDto> getAllLocations() {
        String sql = "SELECT * FROM fn_get_storage_locations()";

        return jdbcTemplate.query(sql, new RowMapper<StorageLocationDto>() {
            @Override
            public StorageLocationDto mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
                StorageLocationDto dto = new StorageLocationDto();
                dto.setLocationId(rs.getLong("location_id"));
                dto.setDescription(rs.getString("description"));
                dto.setStaffId(rs.getLong("staff_id"));
                return dto;
            }
        });
    }

    public long countLocations() {
        Long result = jdbcTemplate.queryForObject(
                "SELECT fn_count_storage_locations()",
                Long.class
        );
        return result == null ? 0L : result;
    }

    public void addLocation(StorageLocationRequest request) {
        jdbcTemplate.update(
                "CALL sp_add_storage_location(?, ?)",
                request.getDescription(),
                request.getStaffId()
        );
    }
}
