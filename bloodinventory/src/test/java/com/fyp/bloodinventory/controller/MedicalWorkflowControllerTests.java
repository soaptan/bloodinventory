package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.MedicalDonorDto;
import com.fyp.bloodinventory.service.MedicalWorkflowService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void donationRecordPageUsesAllDonorsForSelectionList() {
        List<MedicalDonorDto> donors = List.of(donor(1L, "Eligible Donor", true), donor(2L, "Deferred Donor", false));
        when(medicalWorkflowService.getDonors()).thenReturn(donors);
        when(medicalWorkflowService.getStorageLocations()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String viewName = controller.donationRecord(model);

        assertThat(viewName).isEqualTo("medical-donation-record");
        assertThat(model.asMap()).containsEntry("donationDonors", donors);
        verify(medicalWorkflowService).getDonors();
        verify(medicalWorkflowService, never()).getEligibleDonors();
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
