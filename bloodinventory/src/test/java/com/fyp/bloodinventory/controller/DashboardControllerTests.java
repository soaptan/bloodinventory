package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.StorageLocationRequest;
import com.fyp.bloodinventory.dto.DeferralRuleRequest;
import com.fyp.bloodinventory.service.AdminDashboardService;
import com.fyp.bloodinventory.service.AuditTrailService;
import com.fyp.bloodinventory.service.DeferralRuleService;
import com.fyp.bloodinventory.service.InventoryMonitorService;
import com.fyp.bloodinventory.service.LabWorkflowService;
import com.fyp.bloodinventory.service.MedicalWorkflowService;
import com.fyp.bloodinventory.service.ReportsAlertService;
import com.fyp.bloodinventory.service.StaffService;
import com.fyp.bloodinventory.service.StorageLocationService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTests {

    private final AdminDashboardService adminDashboardService = mock(AdminDashboardService.class);
    private final DeferralRuleService deferralRuleService = mock(DeferralRuleService.class);
    private final StorageLocationService storageLocationService = mock(StorageLocationService.class);
    private final InventoryMonitorService inventoryMonitorService = mock(InventoryMonitorService.class);
    private final MedicalWorkflowService medicalWorkflowService = mock(MedicalWorkflowService.class);
    private final LabWorkflowService labWorkflowService = mock(LabWorkflowService.class);
    private final ReportsAlertService reportsAlertService = mock(ReportsAlertService.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final StaffService staffService = mock(StaffService.class);
    private final SystemNotificationService notificationService = mock(SystemNotificationService.class);
    private MockMvc mockMvc;

    private final DashboardController controller = new DashboardController(
            adminDashboardService,
            deferralRuleService,
            storageLocationService,
            inventoryMonitorService,
            medicalWorkflowService,
            labWorkflowService,
            reportsAlertService,
            auditTrailService,
            staffService,
            notificationService
    );

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void duplicateStorageLocationReopensCreateFormWithDescriptionFieldError() {
        Principal principal = () -> "admin.user";
        StorageLocationRequest request = request("Existing Cabinet");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        when(staffService.getStaffIdByUsername("admin.user")).thenReturn(41L);
        doThrow(new RuntimeException("Storage location name already exists."))
                .when(storageLocationService)
                .addLocation(any(StorageLocationRequest.class));

        BindingResult requestErrors = new BeanPropertyBindingResult(request, "locationRequest");
        String view = controller.addStorageLocation(request, requestErrors, principal, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/admin/storage/create");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Please correct the highlighted storage location field.");
        assertThat(redirectAttributes.getFlashAttributes().get("locationRequest"))
                .isSameAs(request);

        Object bindingResult = redirectAttributes.getFlashAttributes()
                .get(BindingResult.MODEL_KEY_PREFIX + "locationRequest");
        assertThat(bindingResult).isInstanceOf(BindingResult.class);
        assertThat(((BindingResult) bindingResult).getFieldError("description").getDefaultMessage())
                .isEqualTo("Storage location name already exists.");
    }

    @Test
    void duplicateDeferralRuleReopensCreateFormWithDescriptionFieldError() throws Exception {
        Principal principal = () -> "admin.user";
        when(staffService.getStaffIdByUsername("admin.user")).thenReturn(41L);
        doThrow(new RuntimeException("Deferral rule name already exists."))
                .when(deferralRuleService)
                .addRule(any(DeferralRuleRequest.class));

        var result = mockMvc.perform(post("/admin/deferral-rules/add")
                        .principal(principal)
                        .param("description", "Recent Fever")
                        .param("defaultCoolingPeriodDays", "14")
                        .param("lockType", "TEMPORARY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/deferral-rules/create"))
                .andExpect(flash().attribute("errorMessage", "Please correct the highlighted deferral rule field."))
                .andReturn();

        Object bindingResult = result.getFlashMap()
                .get(BindingResult.MODEL_KEY_PREFIX + "ruleRequest");
        assertThat(bindingResult).isInstanceOf(BindingResult.class);
        assertThat(((BindingResult) bindingResult).getFieldError("description").getDefaultMessage())
                .isEqualTo("Deferral rule name already exists.");
    }

    private StorageLocationRequest request(String description) {
        StorageLocationRequest request = new StorageLocationRequest();
        request.setDescription(description);
        return request;
    }
}
