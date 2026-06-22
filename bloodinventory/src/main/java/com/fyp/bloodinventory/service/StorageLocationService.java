package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.StorageLocationDto;
import com.fyp.bloodinventory.dto.StorageLocationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class StorageLocationService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseAuditContextService auditContextService;

    public StorageLocationService(JdbcTemplate jdbcTemplate,
                                  DatabaseAuditContextService auditContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditContextService = auditContextService;
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
                dto.setActive(rs.getBoolean("is_active"));
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

    @Transactional
    public void addLocation(StorageLocationRequest request) {
        applyAuditContext();
        jdbcTemplate.update(
                "CALL sp_add_storage_location(?, ?)",
                request.getDescription(),
                request.getStaffId()
        );
    }

    @Transactional
    public void updateLocation(Long locationId, StorageLocationRequest request) {
        applyAuditContext();
        Long requiredLocationId = requireId(locationId, "Please select a storage location.");
        String description = requireText(request.getDescription(), "Please enter a storage description.");
        Long staffId = requireId(request.getStaffId(), "Signed-in administrator account was not found.");

        int updatedRows = jdbcTemplate.update("""
                UPDATE storage_location
                SET description = ?,
                    staff_id = ?
                WHERE location_id = ?
                """, description, staffId, requiredLocationId);

        if (updatedRows == 0) {
            throw new RuntimeException("Storage location was not found.");
        }
    }

    @Transactional
    public void archiveLocation(Long locationId) {
        applyAuditContext();
        Long requiredLocationId = requireId(locationId, "Please select a storage location.");

        int updatedRows = jdbcTemplate.update("""
                UPDATE storage_location
                SET is_active = FALSE
                WHERE location_id = ?
                """, requiredLocationId);
        if (updatedRows == 0) {
            throw new RuntimeException("Storage location was not found.");
        }
    }

    @Transactional
    public void restoreLocation(Long locationId) {
        applyAuditContext();
        Long requiredLocationId = requireId(locationId, "Please select a storage location.");

        int updatedRows = jdbcTemplate.update("""
                UPDATE storage_location
                SET is_active = TRUE
                WHERE location_id = ?
                """, requiredLocationId);
        if (updatedRows == 0) {
            throw new RuntimeException("Storage location was not found.");
        }
    }

    private void applyAuditContext() {
        auditContextService.applyCurrentContext();
    }

    private Long requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new RuntimeException(message);
        }

        return value;
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new RuntimeException(message);
        }

        return normalized;
    }
}
