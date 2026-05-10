package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.LabComponentStatusDto;
import com.fyp.bloodinventory.dto.LabDashboardSummaryDto;
import com.fyp.bloodinventory.dto.LabScreeningRequest;
import com.fyp.bloodinventory.dto.LabTestQueueDto;
import com.fyp.bloodinventory.dto.LabTraceabilityDto;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LabWorkflowService {

    public static final List<String> TTI_RESULTS = List.of("NEGATIVE", "POSITIVE", "PENDING");
    public static final List<String> BLOOD_TYPE_RESULTS = List.of("MATCHED", "MISMATCHED", "PENDING");
    public static final List<String> FINAL_STATUSES = List.of("SAFE", "DISCARDED", "PENDING");
    public static final List<String> COMPONENT_STATUSES = List.of("QUARANTINED", "AVAILABLE", "RESERVED", "DISCARDED", "USED");
    public static final List<String> LAB_COMPONENT_STATUS_OPTIONS = List.of("QUARANTINED", "AVAILABLE", "DISCARDED");

    private final JdbcTemplate jdbcTemplate;

    public LabWorkflowService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LabDashboardSummaryDto getDashboardSummary() {
        return jdbcTemplate.queryForObject("""
                SELECT
                    (
                        SELECT COUNT(DISTINCT dn.donation_id)
                        FROM donation dn
                        LEFT JOIN lab_test latest ON latest.test_id = (
                            SELECT lt.test_id
                            FROM lab_test lt
                            WHERE lt.donation_id = dn.donation_id
                            ORDER BY lt.test_date DESC, lt.test_id DESC
                            LIMIT 1
                        )
                        WHERE EXISTS (
                            SELECT 1
                            FROM blood_component bc
                            WHERE bc.donation_id = dn.donation_id
                              AND UPPER(bc.status) = 'QUARANTINED'
                        )
                        AND COALESCE(UPPER(latest.final_status), 'PENDING') = 'PENDING'
                    )::BIGINT AS pending_tests,
                    (SELECT COUNT(*) FROM lab_test WHERE UPPER(final_status) IN ('SAFE', 'DISCARDED'))::BIGINT AS completed_tests,
                    (SELECT COUNT(*) FROM blood_component WHERE UPPER(status) IN ('AVAILABLE', 'RESERVED'))::BIGINT AS safe_components,
                    (SELECT COUNT(*) FROM blood_component WHERE UPPER(status) = 'DISCARDED')::BIGINT AS discarded_components
                """, (rs, rowNum) -> {
            LabDashboardSummaryDto dto = new LabDashboardSummaryDto();
            dto.setPendingTests(rs.getLong("pending_tests"));
            dto.setCompletedTests(rs.getLong("completed_tests"));
            dto.setSafeComponents(rs.getLong("safe_components"));
            dto.setDiscardedComponents(rs.getLong("discarded_components"));
            return dto;
        });
    }

    public List<LabTestQueueDto> getPendingTests() {
        return jdbcTemplate.query("""
                SELECT *
                FROM (
                    SELECT
                        dn.donation_id,
                        dn.collection_timestamp,
                        d.full_name AS donor_name,
                        d.blood_group,
                        COUNT(bc.component_id)::BIGINT AS component_count,
                        STRING_AGG(DISTINCT bc.component_type, ', ' ORDER BY bc.component_type) AS component_types,
                        STRING_AGG(DISTINCT bc.status, ', ' ORDER BY bc.status) AS component_statuses,
                        latest.test_id,
                        latest.tti_screening,
                        latest.blood_type_match,
                        COALESCE(latest.final_status, 'PENDING') AS final_status,
                        latest.test_date,
                        s.full_name AS staff_name
                    FROM donation dn
                    JOIN donor d ON d.donor_id = dn.donor_id
                    JOIN blood_component bc ON bc.donation_id = dn.donation_id
                    LEFT JOIN LATERAL (
                        SELECT lt.*
                        FROM lab_test lt
                        WHERE lt.donation_id = dn.donation_id
                        ORDER BY lt.test_date DESC, lt.test_id DESC
                        LIMIT 1
                    ) latest ON TRUE
                    LEFT JOIN staff s ON s.staff_id = latest.staff_id
                    GROUP BY dn.donation_id, dn.collection_timestamp, d.full_name, d.blood_group,
                             latest.test_id, latest.tti_screening, latest.blood_type_match,
                             latest.final_status, latest.test_date, s.full_name
                ) queue
                WHERE UPPER(queue.component_statuses) LIKE '%QUARANTINED%'
                  AND UPPER(queue.final_status) = 'PENDING'
                ORDER BY queue.collection_timestamp ASC, queue.donation_id ASC
                """, this::mapQueue);
    }

    public List<LabTestQueueDto> getTestRecords() {
        return jdbcTemplate.query("""
                SELECT
                    dn.donation_id,
                    dn.collection_timestamp,
                    d.full_name AS donor_name,
                    d.blood_group,
                    COUNT(bc.component_id)::BIGINT AS component_count,
                    STRING_AGG(DISTINCT bc.component_type, ', ' ORDER BY bc.component_type) AS component_types,
                    STRING_AGG(DISTINCT bc.status, ', ' ORDER BY bc.status) AS component_statuses,
                    lt.test_id,
                    lt.tti_screening,
                    lt.blood_type_match,
                    lt.final_status,
                    lt.test_date,
                    s.full_name AS staff_name
                FROM lab_test lt
                JOIN donation dn ON dn.donation_id = lt.donation_id
                JOIN donor d ON d.donor_id = dn.donor_id
                JOIN blood_component bc ON bc.donation_id = dn.donation_id
                JOIN staff s ON s.staff_id = lt.staff_id
                GROUP BY dn.donation_id, dn.collection_timestamp, d.full_name, d.blood_group,
                         lt.test_id, lt.tti_screening, lt.blood_type_match,
                         lt.final_status, lt.test_date, s.full_name
                ORDER BY lt.test_date DESC, lt.test_id DESC
                """, this::mapQueue);
    }

    public List<LabComponentStatusDto> getComponentStatuses() {
        return jdbcTemplate.query("""
                SELECT
                    bc.component_id,
                    bc.component_type,
                    bc.expiry_timestamp,
                    bc.status,
                    bc.donation_id,
                    d.full_name AS donor_name,
                    d.blood_group AS donor_blood_group,
                    sl.description AS location_description,
                    latest.final_status,
                    latest.tti_screening,
                    latest.blood_type_match
                FROM blood_component bc
                JOIN donation dn ON dn.donation_id = bc.donation_id
                JOIN donor d ON d.donor_id = dn.donor_id
                JOIN storage_location sl ON sl.location_id = bc.location_id
                LEFT JOIN LATERAL (
                    SELECT lt.final_status, lt.tti_screening, lt.blood_type_match
                    FROM lab_test lt
                    WHERE lt.donation_id = bc.donation_id
                    ORDER BY lt.test_date DESC, lt.test_id DESC
                    LIMIT 1
                ) latest ON TRUE
                ORDER BY
                    CASE UPPER(bc.status)
                        WHEN 'QUARANTINED' THEN 1
                        WHEN 'AVAILABLE' THEN 2
                        WHEN 'RESERVED' THEN 3
                        WHEN 'DISCARDED' THEN 4
                        WHEN 'USED' THEN 5
                        ELSE 6
                    END,
                    bc.expiry_timestamp ASC,
                    bc.component_id ASC
                """, (rs, rowNum) -> {
            LabComponentStatusDto dto = new LabComponentStatusDto();
            dto.setComponentId(rs.getLong("component_id"));
            dto.setComponentType(rs.getString("component_type"));
            dto.setExpiryTimestamp(rs.getTimestamp("expiry_timestamp"));
            dto.setStatus(rs.getString("status"));
            dto.setDonationId(rs.getLong("donation_id"));
            dto.setDonorName(rs.getString("donor_name"));
            dto.setDonorBloodGroup(rs.getString("donor_blood_group"));
            dto.setLocationDescription(rs.getString("location_description"));
            dto.setFinalStatus(rs.getString("final_status"));
            dto.setTtiScreening(rs.getString("tti_screening"));
            dto.setBloodTypeMatch(rs.getString("blood_type_match"));
            return dto;
        });
    }

    public List<LabTraceabilityDto> getTraceabilityRecords() {
        return jdbcTemplate.query("""
                SELECT
                    bc.component_id,
                    bc.component_type,
                    bc.status AS component_status,
                    bc.donation_id,
                    dn.collection_timestamp,
                    d.full_name AS donor_name,
                    d.blood_group AS donor_blood_group,
                    sl.description AS location_description,
                    latest.test_id,
                    latest.final_status AS lab_final_status,
                    latest.test_date,
                    lab_staff.full_name AS lab_staff_name,
                    tr.patient_id,
                    p.name AS patient_name,
                    tr.transfusion_timestamp,
                    transfusion_staff.full_name AS transfusion_staff_name
                FROM blood_component bc
                JOIN donation dn ON dn.donation_id = bc.donation_id
                JOIN donor d ON d.donor_id = dn.donor_id
                JOIN storage_location sl ON sl.location_id = bc.location_id
                LEFT JOIN LATERAL (
                    SELECT lt.*
                    FROM lab_test lt
                    WHERE lt.donation_id = bc.donation_id
                    ORDER BY lt.test_date DESC, lt.test_id DESC
                    LIMIT 1
                ) latest ON TRUE
                LEFT JOIN staff lab_staff ON lab_staff.staff_id = latest.staff_id
                LEFT JOIN transfusion_record tr ON tr.component_id = bc.component_id
                LEFT JOIN patient p ON p.patient_id = tr.patient_id
                LEFT JOIN staff transfusion_staff ON transfusion_staff.staff_id = tr.staff_id
                ORDER BY dn.collection_timestamp DESC, bc.component_id DESC
                """, (rs, rowNum) -> {
            LabTraceabilityDto dto = new LabTraceabilityDto();
            dto.setComponentId(rs.getLong("component_id"));
            dto.setComponentType(rs.getString("component_type"));
            dto.setComponentStatus(rs.getString("component_status"));
            dto.setDonationId(rs.getLong("donation_id"));
            dto.setCollectionTimestamp(rs.getTimestamp("collection_timestamp"));
            dto.setDonorName(rs.getString("donor_name"));
            dto.setDonorBloodGroup(rs.getString("donor_blood_group"));
            dto.setLocationDescription(rs.getString("location_description"));
            dto.setTestId(nullableLong(rs, "test_id"));
            dto.setLabFinalStatus(rs.getString("lab_final_status"));
            dto.setTestDate(rs.getTimestamp("test_date"));
            dto.setLabStaffName(rs.getString("lab_staff_name"));
            dto.setPatientId(nullableLong(rs, "patient_id"));
            dto.setPatientName(rs.getString("patient_name"));
            dto.setTransfusionTimestamp(rs.getTimestamp("transfusion_timestamp"));
            dto.setTransfusionStaffName(rs.getString("transfusion_staff_name"));
            dto.setLifecycleStage(lifecycleStage(dto));
            return dto;
        });
    }

    @Transactional
    public void recordScreening(LabScreeningRequest request, String username) {
        Long donationId = requireId(request.getDonationId(), "Please select a donation from the pending test list.");
        Long staffId = requireLabTechnicianId(username);
        String ttiScreening = normalizeAllowed(request.getTtiScreening(), TTI_RESULTS, "Please select a valid TTI screening result.");
        String bloodTypeMatch = normalizeAllowed(request.getBloodTypeMatch(), BLOOD_TYPE_RESULTS, "Please select a valid blood type match result.");
        String finalStatus = resolveFinalStatus(request.getFinalStatus(), ttiScreening, bloodTypeMatch);
        ensureDonationExists(donationId);

        Long existingTestId = latestTestIdForDonation(donationId);
        if (existingTestId == null) {
            jdbcTemplate.update("""
                    INSERT INTO lab_test (
                        test_id,
                        tti_screening,
                        blood_type_match,
                        final_status,
                        test_date,
                        staff_id,
                        donation_id
                    )
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
                    """, nextId("lab_test", "test_id"), ttiScreening, bloodTypeMatch, finalStatus, staffId, donationId);
        } else {
            jdbcTemplate.update("""
                    UPDATE lab_test
                    SET tti_screening = ?,
                        blood_type_match = ?,
                        final_status = ?,
                        test_date = CURRENT_TIMESTAMP,
                        staff_id = ?
                    WHERE test_id = ?
                    """, ttiScreening, bloodTypeMatch, finalStatus, staffId, existingTestId);
        }

        updateDonationComponentsFromFinalStatus(donationId, finalStatus);
    }

    @Transactional
    public void updateComponentStatus(Long componentId, String status) {
        Long requiredComponentId = requireId(componentId, "Please select a component.");
        String normalizedStatus = normalizeAllowed(status, LAB_COMPONENT_STATUS_OPTIONS, "Please select a valid lab component status.");

        if ("AVAILABLE".equals(normalizedStatus)) {
            Long safeTestCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM blood_component bc
                    LEFT JOIN LATERAL (
                        SELECT lt.final_status
                        FROM lab_test lt
                        WHERE lt.donation_id = bc.donation_id
                        ORDER BY lt.test_date DESC, lt.test_id DESC
                        LIMIT 1
                    ) latest ON TRUE
                    WHERE bc.component_id = ?
                      AND UPPER(latest.final_status) = 'SAFE'
                    """, Long.class, requiredComponentId);
            if (safeTestCount == null || safeTestCount == 0) {
                throw new RuntimeException("Component can be released only after a safe lab screening result.");
            }
        }

        int updatedRows = jdbcTemplate.update("""
                UPDATE blood_component
                SET status = ?
                WHERE component_id = ?
                """, normalizedStatus, requiredComponentId);
        if (updatedRows == 0) {
            throw new RuntimeException("Component record was not found.");
        }
    }

    private LabTestQueueDto mapQueue(@NonNull ResultSet rs, int rowNum) throws SQLException {
        LabTestQueueDto dto = new LabTestQueueDto();
        dto.setDonationId(rs.getLong("donation_id"));
        dto.setCollectionTimestamp(rs.getTimestamp("collection_timestamp"));
        dto.setDonorName(rs.getString("donor_name"));
        dto.setBloodGroup(rs.getString("blood_group"));
        dto.setComponentCount(rs.getLong("component_count"));
        dto.setComponentTypes(rs.getString("component_types"));
        dto.setComponentStatuses(rs.getString("component_statuses"));
        dto.setTestId(nullableLong(rs, "test_id"));
        dto.setTtiScreening(rs.getString("tti_screening"));
        dto.setBloodTypeMatch(rs.getString("blood_type_match"));
        dto.setFinalStatus(rs.getString("final_status"));
        dto.setTestDate(rs.getTimestamp("test_date"));
        dto.setStaffName(rs.getString("staff_name"));
        return dto;
    }

    private void updateDonationComponentsFromFinalStatus(Long donationId, String finalStatus) {
        String componentStatus = switch (finalStatus) {
            case "SAFE" -> "AVAILABLE";
            case "DISCARDED" -> "DISCARDED";
            default -> "QUARANTINED";
        };

        jdbcTemplate.update("""
                UPDATE blood_component
                SET status = ?
                WHERE donation_id = ?
                  AND UPPER(status) IN ('QUARANTINED', 'AVAILABLE', 'DISCARDED')
                """, componentStatus, donationId);
    }

    private String resolveFinalStatus(String requestedFinalStatus, String ttiScreening, String bloodTypeMatch) {
        String normalized = normalizeAllowed(requestedFinalStatus, FINAL_STATUSES, "Please select a valid final status.");
        if ("POSITIVE".equals(ttiScreening) || "MISMATCHED".equals(bloodTypeMatch)) {
            return "DISCARDED";
        }
        if ("PENDING".equals(ttiScreening) || "PENDING".equals(bloodTypeMatch)) {
            return "PENDING";
        }
        return normalized;
    }

    private String lifecycleStage(LabTraceabilityDto dto) {
        String status = normalizeNullable(dto.getComponentStatus());
        if (dto.getTransfusionTimestamp() != null || "USED".equals(status)) {
            return "Transfused";
        }
        if ("DISCARDED".equals(status)) {
            return "Discarded";
        }
        if ("AVAILABLE".equals(status) || "RESERVED".equals(status)) {
            return "Released";
        }
        if (dto.getTestId() != null) {
            return "Lab reviewed";
        }
        return "Pending screening";
    }

    private Long requireLabTechnicianId(String username) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT lt.staff_id
                    FROM lab_technician lt
                    JOIN staff s ON s.staff_id = lt.staff_id
                    WHERE s.username = ?
                    """, Long.class, username);
        } catch (EmptyResultDataAccessException ex) {
            throw new RuntimeException("Signed-in account is not a lab technician account.");
        }
    }

    private void ensureDonationExists(Long donationId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM donation WHERE donation_id = ?", Long.class, donationId);
        if (count == null || count == 0) {
            throw new RuntimeException("Donation record was not found.");
        }
    }

    private Long latestTestIdForDonation(Long donationId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT test_id
                FROM lab_test
                WHERE donation_id = ?
                ORDER BY test_date DESC, test_id DESC
                LIMIT 1
                """, Long.class, donationId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private Long nextId(String tableName, String columnName) {
        Long nextId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(" + columnName + "), 0) + 1 FROM " + tableName,
                Long.class
        );
        return nextId == null ? 1L : nextId;
    }

    private Long requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new RuntimeException(message);
        }
        return value;
    }

    private String normalizeAllowed(String value, List<String> allowedValues, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null || !allowedValues.contains(normalized)) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
