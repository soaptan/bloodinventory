package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.MedicalComponentDto;
import com.fyp.bloodinventory.dto.MedicalDashboardSummaryDto;
import com.fyp.bloodinventory.dto.MedicalDeferralReasonDto;
import com.fyp.bloodinventory.dto.MedicalDeferralRequest;
import com.fyp.bloodinventory.dto.MedicalDonationDto;
import com.fyp.bloodinventory.dto.MedicalDonationRequest;
import com.fyp.bloodinventory.dto.MedicalDonorDto;
import com.fyp.bloodinventory.dto.MedicalDonorRequest;
import com.fyp.bloodinventory.dto.MedicalPatientDto;
import com.fyp.bloodinventory.dto.MedicalSafeMatchRequest;
import com.fyp.bloodinventory.dto.MedicalTransfusionRecordDto;
import com.fyp.bloodinventory.dto.MedicalTransfusionRequest;
import com.fyp.bloodinventory.dto.StorageLocationDto;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.Set;

@Service
public class MedicalWorkflowService {

    public static final List<String> BLOOD_GROUPS = List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
    public static final List<String> COMPONENT_TYPES = List.of("RBC", "PLASMA", "PLATELET");
    private static final String PERMANENT_LOCK = "PERMANENT";
    private static final Pattern PATIENT_NAME_PATTERN = Pattern.compile("^[\\p{L}](?:[\\p{L}\\s.,'@/\\-])*$");

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseAuditContextService auditContextService;

    public MedicalWorkflowService(JdbcTemplate jdbcTemplate,
                                  DatabaseAuditContextService auditContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditContextService = auditContextService;
    }

    public MedicalDashboardSummaryDto getDashboardSummary() {
        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) FILTER (
                        WHERE COALESCE(d.permanent_deferral, FALSE) = FALSE
                          AND (d.deferral_expiry_date IS NULL OR d.deferral_expiry_date < CURRENT_DATE)
                    )::BIGINT AS eligible_donors,
                    COUNT(*) FILTER (
                        WHERE COALESCE(d.permanent_deferral, FALSE) = TRUE
                           OR d.deferral_expiry_date >= CURRENT_DATE
                    )::BIGINT AS deferred_donors,
                    (SELECT COUNT(*) FROM donation WHERE collection_timestamp::DATE = CURRENT_DATE)::BIGINT AS today_donations,
                    (SELECT COUNT(*) FROM blood_component WHERE UPPER(status) = 'QUARANTINED')::BIGINT AS quarantined_components,
                    (SELECT COUNT(*) FROM blood_component WHERE UPPER(status) = 'AVAILABLE')::BIGINT AS available_components,
                    (SELECT COUNT(*) FROM transfusion_record)::BIGINT AS transfusion_events
                FROM donor d
                """, (rs, rowNum) -> {
            MedicalDashboardSummaryDto dto = new MedicalDashboardSummaryDto();
            dto.setEligibleDonors(rs.getLong("eligible_donors"));
            dto.setDeferredDonors(rs.getLong("deferred_donors"));
            dto.setTodayDonations(rs.getLong("today_donations"));
            dto.setQuarantinedComponents(rs.getLong("quarantined_components"));
            dto.setAvailableComponents(rs.getLong("available_components"));
            dto.setTransfusionEvents(rs.getLong("transfusion_events"));
            return dto;
        });
    }

    public List<MedicalDonorDto> getDonors() {
        return jdbcTemplate.query("""
                SELECT
                    d.donor_id,
                    d.ic_number,
                    d.full_name,
                    d.blood_group,
                    d.deferral_expiry_date,
                    d.permanent_deferral,
                    latest.description AS latest_deferral_reason,
                    latest.date_recorded AS latest_deferral_date,
                    latest.lock_type AS latest_deferral_lock_type
                FROM donor d
                LEFT JOIN LATERAL (
                    SELECT dr.description, ddh.date_recorded, dr.lock_type
                    FROM donor_deferral_history ddh
                    JOIN deferral_reason dr ON dr.reason_id = ddh.reason_id
                    WHERE ddh.donor_id = d.donor_id
                    ORDER BY ddh.date_recorded DESC, ddh.reason_id DESC
                    LIMIT 1
                ) latest ON TRUE
                ORDER BY
                    CASE WHEN COALESCE(d.permanent_deferral, FALSE) = TRUE OR d.deferral_expiry_date >= CURRENT_DATE THEN 1 ELSE 0 END DESC,
                    d.full_name ASC
                """, this::mapDonor);
    }

    public List<MedicalDonorDto> getEligibleDonors() {
        return getDonors().stream()
                .filter(MedicalDonorDto::isEligible)
                .toList();
    }

    public List<MedicalDeferralReasonDto> getDeferralReasons() {
        return jdbcTemplate.query("""
                SELECT reason_id, description, default_cooling_period_days, lock_type
                FROM deferral_reason
                WHERE is_active = TRUE
                ORDER BY description ASC
                """, (rs, rowNum) -> {
            MedicalDeferralReasonDto dto = new MedicalDeferralReasonDto();
            dto.setReasonId(rs.getLong("reason_id"));
            dto.setDescription(rs.getString("description"));
            dto.setDefaultCoolingPeriodDays(rs.getInt("default_cooling_period_days"));
            dto.setLockType(rs.getString("lock_type"));
            return dto;
        });
    }

    public List<StorageLocationDto> getStorageLocations() {
        return jdbcTemplate.query("""
                SELECT location_id, description, staff_id, is_active
                FROM storage_location
                WHERE is_active = TRUE
                ORDER BY location_id ASC
                """, (rs, rowNum) -> {
            StorageLocationDto dto = new StorageLocationDto();
            dto.setLocationId(rs.getLong("location_id"));
            dto.setDescription(rs.getString("description"));
            dto.setStaffId(rs.getLong("staff_id"));
            dto.setActive(rs.getBoolean("is_active"));
            return dto;
        });
    }

    public List<MedicalDonationDto> getDonationSessions() {
        return jdbcTemplate.query("""
                SELECT
                    dn.donation_id,
                    dn.collection_timestamp,
                    d.donor_id,
                    d.full_name AS donor_name,
                    d.blood_group,
                    dn.staff_id,
                    s.full_name AS staff_name,
                    MIN(bc.location_id)::BIGINT AS location_id,
                    MIN(sl.description) AS location_description,
                    COUNT(bc.component_id)::BIGINT AS component_count,
                    COALESCE(STRING_AGG(DISTINCT bc.status, ', ' ORDER BY bc.status), 'PENDING') AS component_statuses
                FROM donation dn
                JOIN donor d ON d.donor_id = dn.donor_id
                JOIN staff s ON s.staff_id = dn.staff_id
                LEFT JOIN blood_component bc ON bc.donation_id = dn.donation_id
                LEFT JOIN storage_location sl ON sl.location_id = bc.location_id
                GROUP BY dn.donation_id, dn.collection_timestamp, d.donor_id, d.full_name, d.blood_group, dn.staff_id, s.full_name
                ORDER BY dn.collection_timestamp DESC, dn.donation_id DESC
                """, (rs, rowNum) -> {
            MedicalDonationDto dto = new MedicalDonationDto();
            dto.setDonationId(rs.getLong("donation_id"));
            dto.setCollectionTimestamp(rs.getTimestamp("collection_timestamp"));
            dto.setDonorId(rs.getLong("donor_id"));
            dto.setDonorName(rs.getString("donor_name"));
            dto.setBloodGroup(rs.getString("blood_group"));
            dto.setStaffId(rs.getLong("staff_id"));
            dto.setStaffName(rs.getString("staff_name"));
            dto.setLocationId(nullableLong(rs, "location_id"));
            dto.setLocationDescription(rs.getString("location_description"));
            dto.setComponentCount(rs.getLong("component_count"));
            dto.setComponentStatuses(rs.getString("component_statuses"));
            return dto;
        });
    }

    public List<MedicalComponentDto> getSafeComponents(MedicalSafeMatchRequest request) {
        String requestedType = normalizeNullable(request == null ? null : request.getComponentType());
        String recipientBloodGroup = normalizeBloodGroup(request == null ? null : request.getRecipientBloodGroup());

        List<MedicalComponentDto> components = jdbcTemplate.query("""
                SELECT
                    bc.component_id,
                    bc.component_type,
                    bc.expiry_timestamp,
                    bc.status,
                    bc.donation_id,
                    bc.location_id,
                    sl.description AS location_description,
                    d.donor_id,
                    d.full_name AS donor_name,
                    d.blood_group AS donor_blood_group
                FROM blood_component bc
                JOIN donation dn ON dn.donation_id = bc.donation_id
                JOIN donor d ON d.donor_id = dn.donor_id
                JOIN storage_location sl ON sl.location_id = bc.location_id
                WHERE UPPER(bc.status) IN ('AVAILABLE', 'RESERVED')
                  AND (CAST(? AS VARCHAR) IS NULL OR UPPER(bc.component_type) = ?)
                ORDER BY bc.expiry_timestamp ASC, bc.component_id ASC
                """, (rs, rowNum) -> mapComponent(rs), requestedType, requestedType);

        components.forEach(component -> applyCompatibility(component, recipientBloodGroup));

        return components.stream()
                .sorted(Comparator
                        .comparing(MedicalComponentDto::isCompatible).reversed()
                        .thenComparing(MedicalComponentDto::getExpiryTimestamp)
                        .thenComparing(MedicalComponentDto::getComponentId))
                .toList();
    }

    public List<MedicalComponentDto> getTransfusionReadyComponents() {
        MedicalSafeMatchRequest request = new MedicalSafeMatchRequest();
        return getSafeComponents(request).stream()
                .filter(component -> "AVAILABLE".equalsIgnoreCase(component.getStatus())
                        || "RESERVED".equalsIgnoreCase(component.getStatus()))
                .toList();
    }

    public List<MedicalPatientDto> getPatients() {
        return jdbcTemplate.query("""
                SELECT patient_id, name, condition
                FROM patient
                ORDER BY name ASC
                """, (rs, rowNum) -> {
            MedicalPatientDto dto = new MedicalPatientDto();
            dto.setPatientId(rs.getLong("patient_id"));
            dto.setName(rs.getString("name"));
            dto.setCondition(rs.getString("condition"));
            return dto;
        });
    }

    public List<MedicalTransfusionRecordDto> getTransfusionRecords() {
        return jdbcTemplate.query("""
                SELECT
                    tr.component_id,
                    tr.staff_id,
                    s.full_name AS staff_name,
                    tr.patient_id,
                    p.name AS patient_name,
                    p.condition,
                    tr.transfusion_timestamp,
                    bc.component_type,
                    d.blood_group AS donor_blood_group
                FROM transfusion_record tr
                JOIN staff s ON s.staff_id = tr.staff_id
                JOIN patient p ON p.patient_id = tr.patient_id
                JOIN blood_component bc ON bc.component_id = tr.component_id
                JOIN donation dn ON dn.donation_id = bc.donation_id
                JOIN donor d ON d.donor_id = dn.donor_id
                ORDER BY tr.transfusion_timestamp DESC, tr.component_id DESC
                """, (rs, rowNum) -> {
            MedicalTransfusionRecordDto dto = new MedicalTransfusionRecordDto();
            dto.setComponentId(rs.getLong("component_id"));
            dto.setStaffId(rs.getLong("staff_id"));
            dto.setStaffName(rs.getString("staff_name"));
            dto.setPatientId(rs.getLong("patient_id"));
            dto.setPatientName(rs.getString("patient_name"));
            dto.setCondition(rs.getString("condition"));
            dto.setTransfusionTimestamp(rs.getTimestamp("transfusion_timestamp"));
            dto.setComponentType(rs.getString("component_type"));
            dto.setDonorBloodGroup(rs.getString("donor_blood_group"));
            return dto;
        });
    }

    @Transactional
    public void saveDonor(MedicalDonorRequest request) {
        applyAuditContext();
        String icNumber = requireText(request.getIcNumber(), "Please enter the donor IC number.");
        String fullName = requireText(request.getFullName(), "Please enter the donor full name.");
        String bloodGroup = requireBloodGroup(request.getBloodGroup());
        Long existingDonorId = findDonorIdByIcNumber(icNumber);

        if (request.getDonorId() == null) {
            if (existingDonorId != null) {
                throw new IllegalArgumentException(
                        "This IC number is already registered. Select the existing donor to edit."
                );
            }

            jdbcTemplate.update("""
                    INSERT INTO donor (ic_number, full_name, blood_group, deferral_expiry_date)
                    VALUES (?, ?, ?, NULL)
                    """, icNumber, fullName, bloodGroup);
            return;
        }

        if (existingDonorId != null && !existingDonorId.equals(request.getDonorId())) {
            throw new IllegalArgumentException(
                    "This IC number is already registered. Select the existing donor to edit."
            );
        }

        int updatedRows = jdbcTemplate.update("""
                UPDATE donor
                SET ic_number = ?, full_name = ?, blood_group = ?
                WHERE donor_id = ?
                """, icNumber, fullName, bloodGroup, request.getDonorId());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("The selected donor record could not be found.");
        }
    }

    @Transactional
    public void recordDeferral(MedicalDeferralRequest request, String username) {
        applyAuditContext();
        Long donorId = requireId(request.getDonorId(), "Please select a donor.");
        Long reasonId = requireId(request.getReasonId(), "Please select a deferral reason.");
        ensureDonorExists(donorId);
        Long staffId = requireMedicalStaffId(username);

        DeferralLockRule rule;
        try {
            rule = jdbcTemplate.queryForObject("""
                    SELECT default_cooling_period_days, lock_type
                    FROM deferral_reason
                    WHERE reason_id = ?
                      AND is_active = TRUE
                    """, (rs, rowNum) -> new DeferralLockRule(
                    rs.getInt("default_cooling_period_days"),
                    rs.getString("lock_type")
            ), reasonId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Deferral reason was not found or has been archived.");
        }
        if (rule == null) {
            throw new IllegalArgumentException("Deferral reason was not found or has been archived.");
        }

        boolean permanentLock = rule.isPermanent();
        if (!permanentLock && hasPermanentDeferral(donorId)) {
            throw new IllegalArgumentException(
                    "This donor already has a permanent deferral. Clear it before recording a temporary reason."
            );
        }

        LocalDate expiryDate = permanentLock ? null : LocalDate.now().plusDays(rule.coolingDays());
        jdbcTemplate.update("""
                INSERT INTO donor_deferral_history (donor_id, staff_id, reason_id, date_recorded)
                VALUES (?, ?, ?, CURRENT_DATE)
                ON CONFLICT (donor_id, staff_id, reason_id)
                DO UPDATE SET date_recorded = CURRENT_DATE
                """, donorId, staffId, reasonId);
        jdbcTemplate.update("""
                UPDATE donor
                SET permanent_deferral = ?,
                    deferral_expiry_date = ?
                WHERE donor_id = ?
                """, permanentLock, expiryDate == null ? null : Date.valueOf(expiryDate), donorId);
    }

    @Transactional
    public void clearDeferral(Long donorId) {
        applyAuditContext();
        Long requiredDonorId = requireId(donorId, "Please select a donor.");
        jdbcTemplate.update("""
                UPDATE donor
                SET permanent_deferral = FALSE,
                    deferral_expiry_date = NULL
                WHERE donor_id = ?
                """, requiredDonorId);
    }

    @Transactional
    public void deleteDonor(Long donorId) {
        applyAuditContext();
        Long requiredDonorId = requireId(donorId, "Please select a donor.");
        Long donationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM donation
                WHERE donor_id = ?
                """, Long.class, requiredDonorId);

        if (donationCount != null && donationCount > 0) {
            throw new RuntimeException("Donor cannot be deleted after donation records have been created.");
        }

        int deletedRows = jdbcTemplate.update("DELETE FROM donor WHERE donor_id = ?", requiredDonorId);
        if (deletedRows == 0) {
            throw new RuntimeException("Donor record was not found.");
        }
    }

    @Transactional
    public void recordDonation(MedicalDonationRequest request, String username) {
        applyAuditContext();
        Long donorId = requireDonationId(request.getDonorId(), "Please select a donor.");
        Long locationId = requireDonationId(request.getLocationId(), "Please select a storage location.");
        List<String> componentTypes = normalizedComponentTypes(request.getComponentTypes());
        Timestamp collectionTimestamp = parseDonationTimestamp(request.getCollectionTimestamp());
        Long staffId = requireMedicalStaffId(username);
        ensureActiveStorageLocation(locationId);
        ensureDonorEligibleForDonation(donorId);

        Long donationId = jdbcTemplate.queryForObject("""
                INSERT INTO donation (collection_timestamp, donor_id, staff_id)
                VALUES (?, ?, ?)
                RETURNING donation_id
                """, Long.class, collectionTimestamp, donorId, staffId);

        for (String componentType : componentTypes) {
            jdbcTemplate.update("""
                    INSERT INTO blood_component (
                        component_type,
                        expiry_timestamp,
                        status,
                        donation_id,
                        location_id
                    )
                    VALUES (?, ?, 'QUARANTINED', ?, ?)
                    """,
                    componentType,
                    expiryTimestamp(collectionTimestamp, componentType),
                    donationId,
                    locationId);
        }
    }

    @Transactional
    public void updateDonation(Long donationId, String collectionTimestampValue, Long locationId) {
        applyAuditContext();
        Long requiredDonationId = requireId(donationId, "Please select a donation.");
        Long requiredLocationId = requireId(locationId, "Please select a storage location.");
        Timestamp collectionTimestamp = parseTimestamp(collectionTimestampValue);
        ensureDonationEditable(requiredDonationId);
        ensureActiveStorageLocation(requiredLocationId);

        int updatedRows = jdbcTemplate.update("""
                UPDATE donation
                SET collection_timestamp = ?
                WHERE donation_id = ?
                """, collectionTimestamp, requiredDonationId);
        if (updatedRows == 0) {
            throw new RuntimeException("Donation record was not found.");
        }

        jdbcTemplate.update("""
                UPDATE blood_component
                SET location_id = ?,
                    expiry_timestamp = CASE component_type
                        WHEN 'RBC' THEN CAST(? AS TIMESTAMP) + INTERVAL '42 days'
                        WHEN 'PLATELET' THEN CAST(? AS TIMESTAMP) + INTERVAL '5 days'
                        WHEN 'PLASMA' THEN CAST(? AS TIMESTAMP) + INTERVAL '365 days'
                        ELSE CAST(? AS TIMESTAMP) + INTERVAL '30 days'
                    END
                WHERE donation_id = ?
                """, requiredLocationId, collectionTimestamp, collectionTimestamp, collectionTimestamp,
                collectionTimestamp, requiredDonationId);
    }

    @Transactional
    public void deleteDonation(Long donationId) {
        applyAuditContext();
        Long requiredDonationId = requireId(donationId, "Please select a donation.");
        ensureDonationEditable(requiredDonationId);

        int deletedRows = jdbcTemplate.update("DELETE FROM donation WHERE donation_id = ?", requiredDonationId);
        if (deletedRows == 0) {
            throw new RuntimeException("Donation record was not found.");
        }
    }

    @Transactional
    public void recordTransfusion(MedicalTransfusionRequest request, String username) {
        applyAuditContext();
        Long componentId = requireTransfusionId(request.getComponentId(), "Please select a blood component.");
        String patientMode = resolvePatientMode(request);
        validatePatientFields(request, patientMode);
        Long staffId = requireMedicalStaffId(username);
        ensureTransfusionComponentAvailable(componentId);
        Long patientId = resolvePatientId(request, patientMode);

        jdbcTemplate.update("""
                INSERT INTO transfusion_record (
                    component_id,
                    staff_id,
                    patient_id,
                    transfusion_timestamp
                )
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, componentId, staffId, patientId);
        jdbcTemplate.update("UPDATE blood_component SET status = 'USED' WHERE component_id = ?", componentId);
    }

    @Transactional
    public void updateTransfusion(Long componentId, MedicalTransfusionRequest request) {
        applyAuditContext();
        Long requiredComponentId = requireId(componentId, "Please select a transfusion record.");
        Long patientId = resolvePatientId(request, resolvePatientMode(request));

        int updatedRows = jdbcTemplate.update("""
                UPDATE transfusion_record
                SET patient_id = ?
                WHERE component_id = ?
                """, patientId, requiredComponentId);
        if (updatedRows == 0) {
            throw new RuntimeException("Transfusion record was not found.");
        }
    }

    @Transactional
    public void deleteTransfusion(Long componentId) {
        applyAuditContext();
        Long requiredComponentId = requireId(componentId, "Please select a transfusion record.");
        int deletedRows = jdbcTemplate.update("DELETE FROM transfusion_record WHERE component_id = ?", requiredComponentId);
        if (deletedRows == 0) {
            throw new RuntimeException("Transfusion record was not found.");
        }

        jdbcTemplate.update("""
                UPDATE blood_component
                SET status = 'AVAILABLE'
                WHERE component_id = ?
                  AND UPPER(status) = 'USED'
                """, requiredComponentId);
    }

    @Transactional
    public void reserveComponent(Long componentId) {
        applyAuditContext();
        Long requiredComponentId = requireId(componentId, "Please select a component.");
        int updated = jdbcTemplate.update("""
                UPDATE blood_component
                SET status = 'RESERVED'
                WHERE component_id = ?
                  AND UPPER(status) = 'AVAILABLE'
                """, requiredComponentId);
        if (updated == 0) {
            throw new RuntimeException("Only available components can be reserved.");
        }
    }

    @Transactional
    public void releaseComponent(Long componentId) {
        applyAuditContext();
        Long requiredComponentId = requireId(componentId, "Please select a component.");
        int updated = jdbcTemplate.update("""
                UPDATE blood_component
                SET status = 'AVAILABLE'
                WHERE component_id = ?
                  AND UPPER(status) = 'RESERVED'
                """, requiredComponentId);
        if (updated == 0) {
            throw new RuntimeException("Only reserved components can be released.");
        }
    }

    private void applyAuditContext() {
        auditContextService.applyCurrentContext();
    }

    private MedicalDonorDto mapDonor(@NonNull ResultSet rs, int rowNum) throws SQLException {
        MedicalDonorDto dto = new MedicalDonorDto();
        dto.setDonorId(rs.getLong("donor_id"));
        dto.setIcNumber(rs.getString("ic_number"));
        dto.setFullName(rs.getString("full_name"));
        dto.setBloodGroup(rs.getString("blood_group"));
        dto.setDeferralExpiryDate(toLocalDate(rs.getDate("deferral_expiry_date")));
        dto.setPermanentDeferral(rs.getBoolean("permanent_deferral"));
        dto.setLatestDeferralReason(rs.getString("latest_deferral_reason"));
        dto.setLatestDeferralDate(toLocalDate(rs.getDate("latest_deferral_date")));
        dto.setLatestDeferralLockType(rs.getString("latest_deferral_lock_type"));

        boolean eligible = !dto.isPermanentDeferral()
                && (dto.getDeferralExpiryDate() == null || dto.getDeferralExpiryDate().isBefore(LocalDate.now()));
        dto.setEligible(eligible);
        dto.setEligibilityStatus(eligible ? "ELIGIBLE" : dto.isPermanentDeferral() ? "PERMANENT DEFERRAL" : "DEFERRED");
        return dto;
    }

    private MedicalComponentDto mapComponent(ResultSet rs) throws SQLException {
        MedicalComponentDto dto = new MedicalComponentDto();
        dto.setComponentId(rs.getLong("component_id"));
        dto.setComponentType(rs.getString("component_type"));
        dto.setExpiryTimestamp(rs.getTimestamp("expiry_timestamp"));
        dto.setStatus(rs.getString("status"));
        dto.setDonationId(rs.getLong("donation_id"));
        dto.setLocationId(rs.getLong("location_id"));
        dto.setLocationDescription(rs.getString("location_description"));
        dto.setDonorId(rs.getLong("donor_id"));
        dto.setDonorName(rs.getString("donor_name"));
        dto.setDonorBloodGroup(rs.getString("donor_blood_group"));
        dto.setCompatible(true);
        dto.setCompatibilityNote("No recipient group selected");
        return dto;
    }

    private void applyCompatibility(MedicalComponentDto component, String recipientBloodGroup) {
        if (recipientBloodGroup == null) {
            component.setCompatible(true);
            component.setCompatibilityNote("Available for review");
            return;
        }

        boolean compatible = isCompatible(
                component.getDonorBloodGroup(),
                recipientBloodGroup,
                component.getComponentType()
        );
        component.setCompatible(compatible);
        component.setCompatibilityNote(compatible ? "Compatible" : "Not compatible");
    }

    private boolean isCompatible(String donorBloodGroup, String recipientBloodGroup, String componentType) {
        String donor = normalizeBloodGroup(donorBloodGroup);
        String recipient = normalizeBloodGroup(recipientBloodGroup);
        String type = normalizeNullable(componentType);
        if (donor == null || recipient == null || type == null) {
            return false;
        }

        if ("PLASMA".equals(type)) {
            String donorAbo = aboGroup(donor);
            String recipientAbo = aboGroup(recipient);
            return switch (donorAbo) {
                case "AB" -> true;
                case "A" -> Set.of("A", "O").contains(recipientAbo);
                case "B" -> Set.of("B", "O").contains(recipientAbo);
                case "O" -> "O".equals(recipientAbo);
                default -> false;
            };
        }

        if ("PLATELET".equals(type)) {
            return aboGroup(donor).equals(aboGroup(recipient)) || "O-".equals(donor);
        }

        return switch (recipient) {
            case "O-" -> donor.equals("O-");
            case "O+" -> Set.of("O-", "O+").contains(donor);
            case "A-" -> Set.of("O-", "A-").contains(donor);
            case "A+" -> Set.of("O-", "O+", "A-", "A+").contains(donor);
            case "B-" -> Set.of("O-", "B-").contains(donor);
            case "B+" -> Set.of("O-", "O+", "B-", "B+").contains(donor);
            case "AB-" -> Set.of("O-", "A-", "B-", "AB-").contains(donor);
            case "AB+" -> true;
            default -> false;
        };
    }

    private String aboGroup(String bloodGroup) {
        return bloodGroup.replace("+", "").replace("-", "");
    }

    private Long resolvePatientId(MedicalTransfusionRequest request, String patientMode) {
        if ("existing".equals(patientMode)) {
            Long patientId = requireTransfusionId(request.getPatientId(), "Please select an existing patient.");
            Long patientCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM patient WHERE patient_id = ?",
                    Long.class,
                    patientId
            );
            if (patientCount == null || patientCount == 0) {
                throw new IllegalArgumentException("The selected patient record could not be found.");
            }

            String condition = validateCondition(request.getCondition());
            if (condition != null) {
                jdbcTemplate.update("UPDATE patient SET condition = ? WHERE patient_id = ?", condition, patientId);
            }
            return patientId;
        }

        if (request.getPatientId() != null) {
            throw new IllegalArgumentException("Choose either an existing patient or register a new patient.");
        }

        String patientName = requirePatientName(request.getPatientName());
        String condition = validateCondition(request.getCondition());
        return jdbcTemplate.queryForObject("""
                INSERT INTO patient (name, condition)
                VALUES (?, ?)
                RETURNING patient_id
                """, Long.class, patientName, condition);
    }

    private String resolvePatientMode(MedicalTransfusionRequest request) {
        String patientMode = trimToNull(request.getPatientMode());
        if (patientMode == null) {
            return request.getPatientId() == null ? "new" : "existing";
        }

        String normalizedMode = patientMode.toLowerCase(Locale.ROOT);
        if (!Set.of("existing", "new").contains(normalizedMode)) {
            throw new IllegalArgumentException("Please choose a valid patient type.");
        }
        return normalizedMode;
    }

    private void validatePatientFields(MedicalTransfusionRequest request, String patientMode) {
        if ("new".equals(patientMode)) {
            requirePatientName(request.getPatientName());
        } else {
            requireTransfusionId(request.getPatientId(), "Please select an existing patient.");
        }
        validateCondition(request.getCondition());
    }

    private String requirePatientName(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Please enter the patient name.");
        }
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new IllegalArgumentException("Patient name must be between 2 and 100 characters.");
        }
        if (!PATIENT_NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Patient name can only contain letters and common name punctuation.");
        }
        return normalized;
    }

    private String validateCondition(String value) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > 200) {
            throw new IllegalArgumentException("Patient condition must be 200 characters or fewer.");
        }
        return normalized;
    }

    private void ensureTransfusionComponentAvailable(Long componentId) {
        Long componentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM blood_component WHERE component_id = ?",
                Long.class,
                componentId
        );
        if (componentCount == null || componentCount == 0) {
            throw new IllegalArgumentException("The selected blood component could not be found.");
        }

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM blood_component WHERE component_id = ?",
                String.class,
                componentId
        );
        if (status == null || (!status.equalsIgnoreCase("AVAILABLE") && !status.equalsIgnoreCase("RESERVED"))) {
            throw new IllegalArgumentException("Only available or reserved components can be transfused.");
        }

        Long existingEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfusion_record WHERE component_id = ?",
                Long.class,
                componentId
        );
        if (existingEvents != null && existingEvents > 0) {
            throw new IllegalArgumentException("This component already has a transfusion record.");
        }
    }

    private Long requireMedicalStaffId(String username) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT ms.staff_id
                    FROM medical_staff ms
                    JOIN staff s ON s.staff_id = ms.staff_id
                    WHERE s.username = ?
                    """, Long.class, username);
        } catch (EmptyResultDataAccessException ex) {
            throw new RuntimeException("Signed-in account is not a medical staff account.");
        }
    }

    private Long findDonorIdByIcNumber(String icNumber) {
        List<Long> donorIds = jdbcTemplate.queryForList(
                "SELECT donor_id FROM donor WHERE UPPER(ic_number) = UPPER(?)",
                Long.class,
                icNumber
        );
        return donorIds.isEmpty() ? null : donorIds.get(0);
    }

    private void ensureDonorExists(Long donorId) {
        Long donorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM donor WHERE donor_id = ?",
                Long.class,
                donorId
        );
        if (donorCount == null || donorCount == 0) {
            throw new IllegalArgumentException("The selected donor record could not be found.");
        }
    }

    private void ensureDonorEligibleForDonation(Long donorId) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT COALESCE(permanent_deferral, FALSE) = FALSE
                       AND (deferral_expiry_date IS NULL OR deferral_expiry_date < CURRENT_DATE)
                FROM donor
                WHERE donor_id = ?
                """, Boolean.class, donorId);
        if (result == null) {
            throw new IllegalArgumentException("The selected donor record could not be found.");
        }
        if (!result) {
            throw new IllegalArgumentException("This donor is currently deferred and cannot be collected.");
        }
    }

    private boolean hasPermanentDeferral(Long donorId) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT COALESCE(permanent_deferral, FALSE)
                FROM donor
                WHERE donor_id = ?
                """, Boolean.class, donorId);
        return Boolean.TRUE.equals(result);
    }

    private void ensureDonationEditable(Long donationId) {
        Long protectedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM blood_component bc
                WHERE bc.donation_id = ?
                  AND (
                      UPPER(bc.status) IN ('RESERVED', 'USED')
                      OR EXISTS (
                          SELECT 1
                          FROM transfusion_record tr
                          WHERE tr.component_id = bc.component_id
                      )
                  )
                """, Long.class, donationId);

        if (protectedCount != null && protectedCount > 0) {
            throw new RuntimeException("Donation cannot be changed after a component is reserved or transfused.");
        }
    }

    private void ensureActiveStorageLocation(Long locationId) {
        Long activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM storage_location
                WHERE location_id = ?
                  AND is_active = TRUE
                """, Long.class, locationId);

        if (activeCount == null || activeCount == 0) {
            throw new IllegalArgumentException("Storage location was not found or has been archived.");
        }
    }

    private Timestamp parseDonationTimestamp(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Collection timestamp is required.");
        }

        LocalDateTime collectionTimestamp;
        try {
            collectionTimestamp = LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Collection timestamp must be a valid date and time.");
        }

        if (collectionTimestamp.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Collection timestamp cannot be in the future.");
        }

        return Timestamp.valueOf(collectionTimestamp);
    }

    private Timestamp parseTimestamp(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return Timestamp.valueOf(LocalDateTime.now());
        }

        return Timestamp.valueOf(LocalDateTime.parse(normalized));
    }

    private Timestamp expiryTimestamp(Timestamp collectionTimestamp, String componentType) {
        int days = switch (componentType) {
            case "RBC" -> 42;
            case "PLATELET" -> 5;
            case "PLASMA" -> 365;
            default -> 30;
        };
        return Timestamp.valueOf(collectionTimestamp.toLocalDateTime().plusDays(days));
    }

    private List<String> normalizedComponentTypes(List<String> componentTypes) {
        if (componentTypes == null || componentTypes.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one component type.");
        }

        List<String> normalizedTypes = componentTypes.stream()
                .map(this::normalizeNullable)
                .toList();
        if (normalizedTypes.stream().anyMatch(value -> value == null || !COMPONENT_TYPES.contains(value))) {
            throw new IllegalArgumentException("Select only supported blood component types.");
        }

        return normalizedTypes.stream().distinct().toList();
    }

    private Long requireDonationId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private Long requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new RuntimeException(message);
        }

        return value;
    }

    private Long requireTransfusionId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String requireBloodGroup(String bloodGroup) {
        String normalized = normalizeBloodGroup(bloodGroup);
        if (normalized == null || !BLOOD_GROUPS.contains(normalized)) {
            throw new RuntimeException("Please select a valid blood group.");
        }

        return normalized;
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }

        return normalized;
    }

    private String normalizeBloodGroup(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }

        return normalized.replace(" ", "");
    }

    private String normalizeNullable(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private record DeferralLockRule(int coolingDays, String lockType) {
        private boolean isPermanent() {
            return PERMANENT_LOCK.equalsIgnoreCase(lockType);
        }
    }
}
