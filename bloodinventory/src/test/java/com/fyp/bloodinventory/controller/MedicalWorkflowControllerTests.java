package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.MedicalDonationRequest;
import com.fyp.bloodinventory.dto.MedicalDeferralRequest;
import com.fyp.bloodinventory.dto.MedicalDonorDto;
import com.fyp.bloodinventory.dto.MedicalDonorRequest;
import com.fyp.bloodinventory.dto.MedicalTransfusionRequest;
import com.fyp.bloodinventory.service.MedicalWorkflowService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicalWorkflowControllerTests {

    private final MedicalWorkflowService medicalWorkflowService = mock(MedicalWorkflowService.class);
    private final SystemNotificationService notificationService = mock(SystemNotificationService.class);
    private final MedicalWorkflowController controller = new MedicalWorkflowController(
            medicalWorkflowService,
            notificationService
    );
    private final Principal principal = () -> "medical.user";

    @Test
    void donationRecordPageUsesAllDonorsForSelectionList() {
        List<MedicalDonorDto> donors = List.of(donor(1L, "Eligible Donor", true), donor(2L, "Deferred Donor", false));
        when(medicalWorkflowService.getDonors()).thenReturn(donors);
        when(medicalWorkflowService.getStorageLocations()).thenReturn(List.of());
        when(medicalWorkflowService.getDonationSessions()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String viewName = controller.donationRecord(model);

        assertThat(viewName).isEqualTo("medical-donations");
        assertThat(model.asMap()).containsEntry("donationDonors", donors);
        verify(medicalWorkflowService).getDonors();
        verify(medicalWorkflowService, never()).getEligibleDonors();
    }

    @Test
    void assessmentRouteReturnsDonorRecordsViewWithModalOpen() {
        when(medicalWorkflowService.getDonors()).thenReturn(List.of());
        when(medicalWorkflowService.getDeferralReasons()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.donorAssessment(null, model)).isEqualTo("medical-donor-eligibility");
        assertThat(model.getAttribute("openDonorAssessmentModal")).isEqualTo(true);
    }

    @Test
    void donationRecordRouteReturnsDonationLogViewWithModalOpen() {
        when(medicalWorkflowService.getDonors()).thenReturn(List.of());
        when(medicalWorkflowService.getStorageLocations()).thenReturn(List.of());
        when(medicalWorkflowService.getDonationSessions()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.donationRecord(model)).isEqualTo("medical-donations");
        assertThat(model.getAttribute("openDonationCreateModal")).isEqualTo(true);
    }

    @Test
    void transfusionRecordRouteReturnsTransfusionLogViewWithModalOpen() {
        when(medicalWorkflowService.getPatients()).thenReturn(List.of());
        when(medicalWorkflowService.getTransfusionReadyComponents()).thenReturn(List.of());
        when(medicalWorkflowService.getTransfusionRecords()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.transfusionRecord(model)).isEqualTo("medical-transfusion");
        assertThat(model.getAttribute("openTransfusionCreateModal")).isEqualTo(true);
    }

    @Test
    void successfulDonationRedirectsBackToDonationLog() {
        MedicalDonationRequest request = new MedicalDonationRequest();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "donationRequest");

        assertThat(controller.recordDonation(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/donations");
    }

    @Test
    void failedDonationRedirectsBackToOpenCollectionModal() {
        MedicalDonationRequest request = new MedicalDonationRequest();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "donationRequest");
        doThrow(new RuntimeException("Donation could not be recorded."))
                .when(medicalWorkflowService)
                .recordDonation(any(MedicalDonationRequest.class), any(String.class));

        assertThat(controller.recordDonation(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/donations/record");
    }

    @Test
    void donationValidationErrorIsShownAgainstTheStorageLocationField() {
        MedicalDonationRequest request = new MedicalDonationRequest();
        request.setDonorId(3L);
        request.setLocationId(4L);
        request.setCollectionTimestamp("2026-08-30T10:00");
        request.setComponentTypes(List.of("RBC"));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "donationRequest");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("Storage location was not found or has been archived."))
                .when(medicalWorkflowService)
                .recordDonation(request, "medical.user");

        assertThat(controller.recordDonation(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/donations/record");

        BindingResult storedResult = (BindingResult) redirectAttributes.getFlashAttributes()
                .get(BindingResult.MODEL_KEY_PREFIX + "donationRequest");
        assertThat(storedResult.getFieldError("locationId").getDefaultMessage())
                .isEqualTo("Storage location was not found or has been archived.");
    }

    @Test
    void successfulTransfusionRedirectsBackToTransfusionLog() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "transfusionRequest");

        assertThat(controller.recordTransfusion(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/transfusion");
    }

    @Test
    void failedTransfusionRedirectsBackToOpenEventModal() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "transfusionRequest");
        doThrow(new RuntimeException("Transfusion could not be recorded."))
                .when(medicalWorkflowService)
                .recordTransfusion(any(MedicalTransfusionRequest.class), any(String.class));

        assertThat(controller.recordTransfusion(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/transfusion/record");
    }

    @Test
    void transfusionValidationErrorIsShownAgainstThePatientField() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        request.setPatientMode("existing");
        request.setPatientId(99L);
        request.setComponentId(8L);
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "transfusionRequest");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("The selected patient record could not be found."))
                .when(medicalWorkflowService)
                .recordTransfusion(request, "medical.user");

        assertThat(controller.recordTransfusion(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/transfusion/record");

        assertThat(redirectAttributes.getFlashAttributes().get("transfusionRequest")).isSameAs(request);
        BindingResult storedResult = (BindingResult) redirectAttributes.getFlashAttributes()
                .get(BindingResult.MODEL_KEY_PREFIX + "transfusionRequest");
        assertThat(storedResult.getFieldError("patientId").getDefaultMessage())
                .isEqualTo("The selected patient record could not be found.");
    }

    @Test
    void failedDonorAssessmentRedirectsBackToSelectedAssessmentModal() {
        MedicalDonorRequest request = new MedicalDonorRequest();
        request.setDonorId(42L);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "donorRequest");
        doThrow(new RuntimeException("Donor could not be saved."))
                .when(medicalWorkflowService)
                .saveDonor(any(MedicalDonorRequest.class));

        assertThat(controller.saveDonor(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/donor-eligibility/assessment?donorId=42");
    }

    @Test
    void invalidDonorIdentityPreservesFieldErrorsInTheAssessmentModal() {
        MedicalDonorRequest request = new MedicalDonorRequest();
        request.setFullName("John123");
        request.setIcNumber("invalid");
        request.setBloodGroup("A+");
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "donorRequest");
        bindingResult.rejectValue("icNumber", "pattern", "Enter a valid Malaysian IC number.");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        assertThat(controller.saveDonor(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/donor-eligibility/assessment");
        assertThat(redirectAttributes.getFlashAttributes())
                .containsKey(BindingResult.MODEL_KEY_PREFIX + "donorRequest");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Please correct the highlighted donor fields.");
        assertThat(redirectAttributes.getFlashAttributes().get("donorRequest")).isSameAs(request);
        verify(medicalWorkflowService, never()).saveDonor(any(MedicalDonorRequest.class));
    }

    @Test
    void duplicateDonorIcIsShownAgainstTheIcField() {
        MedicalDonorRequest request = new MedicalDonorRequest();
        request.setFullName("Nur A/P Ali");
        request.setIcNumber("900101-10-1234");
        request.setBloodGroup("A+");
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "donorRequest");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("This IC number is already registered. Select the existing donor to edit."))
                .when(medicalWorkflowService)
                .saveDonor(request);

        assertThat(controller.saveDonor(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/donor-eligibility/assessment");

        BindingResult storedResult = (BindingResult) redirectAttributes.getFlashAttributes()
                .get(BindingResult.MODEL_KEY_PREFIX + "donorRequest");
        assertThat(storedResult.getFieldError("icNumber").getDefaultMessage())
                .isEqualTo("This IC number is already registered. Select the existing donor to edit.");
    }

    @Test
    void invalidDeferralSelectionPreservesTheSelectedValuesAndErrors() {
        MedicalDeferralRequest request = new MedicalDeferralRequest();
        request.setDonorId(12L);
        request.setReasonId(null);
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "deferralRequest");
        bindingResult.rejectValue("reasonId", "required", "Please select a deferral reason.");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        assertThat(controller.recordDeferral(request, bindingResult, principal, redirectAttributes))
                .isEqualTo("redirect:/medical/donor-eligibility/assessment?donorId=12");
        assertThat(redirectAttributes.getFlashAttributes())
                .containsKey(BindingResult.MODEL_KEY_PREFIX + "deferralRequest");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Please correct the highlighted deferral fields.");
        assertThat(redirectAttributes.getFlashAttributes().get("deferralRequest")).isSameAs(request);
        verify(medicalWorkflowService, never()).recordDeferral(any(MedicalDeferralRequest.class), any(String.class));
    }

    private MedicalDonorDto donor(Long donorId, String fullName, boolean eligible) {
        MedicalDonorDto donor = new MedicalDonorDto();
        donor.setDonorId(donorId);
        donor.setFullName(fullName);
        donor.setBloodGroup("A+");
        donor.setEligible(eligible);
        donor.setEligibilityStatus(eligible ? "ELIGIBLE" : "DEFERRED");
        return donor;
    }
}
