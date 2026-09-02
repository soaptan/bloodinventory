package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.MedicalDeferralRequest;
import com.fyp.bloodinventory.dto.MedicalDonationRequest;
import com.fyp.bloodinventory.dto.MedicalDonorRequest;
import com.fyp.bloodinventory.dto.MedicalTransfusionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"all", "null"})
class MedicalWorkflowServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseAuditContextService auditContextService = mock(DatabaseAuditContextService.class);
    private final MedicalWorkflowService service = new MedicalWorkflowService(jdbcTemplate, auditContextService);

    @Test
    void permanentDeferralSetsPermanentLockWithoutExpiryDate() throws Exception {
        MedicalDeferralRequest request = deferralRequest(4L, 7L);
        stubExistingDonor(4L);
        when(jdbcTemplate.queryForObject(contains("FROM medical_staff"), eq(Long.class), eq("medical")))
                .thenReturn(12L);
        stubDeferralRule(7L, 0, "PERMANENT");

        service.recordDeferral(request, "medical");

        verify(jdbcTemplate).update(contains("UPDATE donor"), eq(true), isNull(), eq(4L));
    }

    @Test
    void temporaryDeferralSetsExpiryDateAndClearsPermanentLock() throws Exception {
        MedicalDeferralRequest request = deferralRequest(5L, 8L);
        stubExistingDonor(5L);
        when(jdbcTemplate.queryForObject(contains("FROM medical_staff"), eq(Long.class), eq("medical")))
                .thenReturn(12L);
        stubDeferralRule(8L, 14, "TEMPORARY");
        when(jdbcTemplate.queryForObject(contains("COALESCE(permanent_deferral"), eq(Boolean.class), eq(5L)))
                .thenReturn(false);

        service.recordDeferral(request, "medical");

        verify(jdbcTemplate).update(contains("UPDATE donor"), eq(false), ArgumentMatchers.any(Date.class), eq(5L));
    }

    @Test
    void newDonorWithExistingIcIsRejectedInsteadOfUpdatingTheExistingRecord() {
        MedicalDonorRequest request = new MedicalDonorRequest();
        request.setFullName("Nur A/P Ali");
        request.setIcNumber("900101-10-1234");
        request.setBloodGroup("A+");
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("900101-10-1234")))
                .thenReturn(List.of(17L));

        assertThatThrownBy(() -> service.saveDonor(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("This IC number is already registered. Select the existing donor to edit.");
    }

    @Test
    void donorCanKeepTheirOwnIcNumberWhileEditingTheirRecord() {
        MedicalDonorRequest request = new MedicalDonorRequest();
        request.setDonorId(17L);
        request.setFullName("Nur A/P Ali");
        request.setIcNumber("900101-10-1234");
        request.setBloodGroup("A+");
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("900101-10-1234")))
                .thenReturn(List.of(17L));
        when(jdbcTemplate.update(contains("UPDATE donor"), eq("900101-10-1234"), eq("Nur A/P Ali"), eq("A+"), eq(17L)))
                .thenReturn(1);

        service.saveDonor(request);

        verify(jdbcTemplate).update(contains("UPDATE donor"), eq("900101-10-1234"), eq("Nur A/P Ali"), eq("A+"), eq(17L));
    }

    @Test
    void futureCollectionTimestampIsRejected() {
        MedicalDonationRequest request = donationRequest(
                LocalDateTime.now().plusDays(1).withNano(0).toString(),
                List.of("RBC")
        );
        stubDonationDependencies();

        assertThatThrownBy(() -> service.recordDonation(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Collection timestamp cannot be in the future.");
    }

    @Test
    void forgedComponentTypeIsRejectedInsteadOfBeingSilentlyIgnored() {
        MedicalDonationRequest request = donationRequest(
                LocalDateTime.now().minusMinutes(2).withNano(0).toString(),
                List.of("RBC", "UNSUPPORTED")
        );
        stubDonationDependencies();

        assertThatThrownBy(() -> service.recordDonation(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Select only supported blood component types.");
    }

    @Test
    void invalidNewPatientNameIsRejectedBeforePatientCreation() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        request.setPatientMode("new");
        request.setPatientName("Patient123");
        request.setComponentId(8L);

        assertThatThrownBy(() -> service.recordTransfusion(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patient name can only contain letters and common name punctuation.");
    }

    @Test
    void missingExistingPatientIsRejectedInsteadOfCreatingATransfusion() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        request.setPatientMode("existing");
        request.setPatientId(999L);
        request.setComponentId(8L);
        stubAvailableTransfusionComponent(8L);
        when(jdbcTemplate.queryForObject(contains("FROM medical_staff"), eq(Long.class), eq("medical")))
                .thenReturn(12L);
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM patient"), eq(Long.class), eq(999L)))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.recordTransfusion(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The selected patient record could not be found.");
    }

    @Test
    void forgedTransfusionComponentIsRejected() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        request.setPatientMode("existing");
        request.setPatientId(17L);
        request.setComponentId(999L);

        assertThatThrownBy(() -> service.recordTransfusion(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The selected blood component could not be found.");
    }

    @Test
    void usedComponentIsRejectedBeforeRecordingAnotherTransfusion() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        request.setPatientMode("existing");
        request.setPatientId(17L);
        request.setComponentId(8L);
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM blood_component"), eq(Long.class), eq(8L)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("SELECT status"), eq(String.class), eq(8L)))
                .thenReturn("USED");

        assertThatThrownBy(() -> service.recordTransfusion(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only available or reserved components can be transfused.");
    }

    @Test
    void missingDonationDonorIsReportedAsMissingRatherThanDeferred() {
        MedicalDonationRequest request = donationRequest(
                LocalDateTime.now().minusMinutes(2).withNano(0).toString(),
                List.of("RBC")
        );
        when(jdbcTemplate.queryForObject(contains("FROM medical_staff"), eq(Long.class), eq("medical")))
                .thenReturn(12L);
        when(jdbcTemplate.queryForObject(contains("FROM storage_location"), eq(Long.class), eq(4L)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.recordDonation(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The selected donor record could not be found.");
    }

    @Test
    void deferredDonationDonorIsRejectedBeforeRecording() {
        MedicalDonationRequest request = donationRequest(
                LocalDateTime.now().minusMinutes(2).withNano(0).toString(),
                List.of("RBC")
        );
        when(jdbcTemplate.queryForObject(contains("FROM medical_staff"), eq(Long.class), eq("medical")))
                .thenReturn(12L);
        when(jdbcTemplate.queryForObject(contains("FROM storage_location"), eq(Long.class), eq(4L)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("COALESCE(permanent_deferral"), eq(Boolean.class), eq(3L)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.recordDonation(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("This donor is currently deferred and cannot be collected.");
    }

    @Test
    void deferralRejectsAnUnknownDonorBeforeRecordingHistory() {
        MedicalDeferralRequest request = deferralRequest(999L, 7L);
        when(jdbcTemplate.queryForObject(contains("FROM donor"), eq(Long.class), eq(999L)))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.recordDeferral(request, "medical"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The selected donor record could not be found.");
    }

    private MedicalDeferralRequest deferralRequest(Long donorId, Long reasonId) {
        MedicalDeferralRequest request = new MedicalDeferralRequest();
        request.setDonorId(donorId);
        request.setReasonId(reasonId);
        return request;
    }

    private void stubExistingDonor(Long donorId) {
        when(jdbcTemplate.queryForObject(contains("FROM donor"), eq(Long.class), eq(donorId)))
                .thenReturn(1L);
    }

    private MedicalDonationRequest donationRequest(String timestamp, List<String> componentTypes) {
        MedicalDonationRequest request = new MedicalDonationRequest();
        request.setDonorId(3L);
        request.setLocationId(4L);
        request.setCollectionTimestamp(timestamp);
        request.setComponentTypes(componentTypes);
        return request;
    }

    private void stubDonationDependencies() {
        when(jdbcTemplate.queryForObject(contains("FROM medical_staff"), eq(Long.class), eq("medical")))
                .thenReturn(12L);
        when(jdbcTemplate.queryForObject(contains("FROM storage_location"), eq(Long.class), eq(4L)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("COALESCE(permanent_deferral"), eq(Boolean.class), eq(3L)))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(contains("INSERT INTO donation"), eq(Long.class), ArgumentMatchers.any(), eq(3L), eq(12L)))
                .thenReturn(24L);
    }

    private void stubAvailableTransfusionComponent(Long componentId) {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM blood_component"), eq(Long.class), eq(componentId)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("SELECT status"), eq(String.class), eq(componentId)))
                .thenReturn("AVAILABLE");
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM transfusion_record"), eq(Long.class), eq(componentId)))
                .thenReturn(0L);
    }

    private void stubDeferralRule(Long reasonId, int coolingDays, String lockType) throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), ArgumentMatchers.<RowMapper<?>>any(), eq(reasonId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getInt("default_cooling_period_days")).thenReturn(coolingDays);
                    when(rs.getString("lock_type")).thenReturn(lockType);
                    return mapper.mapRow(rs, 0);
                });
    }
}
