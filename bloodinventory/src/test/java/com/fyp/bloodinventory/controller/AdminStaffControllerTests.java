package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.service.StaffService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStaffControllerTests {

    private final StaffService staffService = mock(StaffService.class);
    private final SystemNotificationService notificationService = mock(SystemNotificationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminStaffController(staffService, notificationService))
                .setValidator(validator)
                .build();
    }

    @Test
    void invalidStaffEditReopensTheModalWithFieldErrorsWithoutUpdating() throws Exception {
        Principal principal = () -> "admin.user";

        var result = mockMvc.perform(post("/admin/staff/42/update")
                        .principal(principal)
                        .param("fullName", "<script>")
                        .param("username", "bad username")
                        .param("password", "weak")
                        .param("icNumber", "123")
                        .param("email", "not-an-email")
                        .param("phoneNo", "letters")
                        .param("gender", "UNKNOWN")
                        .param("staffType", "MEDICAL_STAFF")
                        .param("active", "true")
                        .param("licenseNo", "")
                        .param("position", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/staff/management"))
                .andExpect(flash().attribute("editingStaffId", 42L))
                .andExpect(flash().attribute("errorMessage", "Please correct the highlighted staff profile fields."))
                .andReturn();

        Object errors = result.getFlashMap().get("editFieldErrors");
        assertThat(errors).isInstanceOf(Map.class);
        Set<String> errorFields = ((Map<?, ?>) errors).keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());
        assertThat(errorFields).contains(
                "fullName",
                "username",
                "password",
                "icNumber",
                "email",
                "phoneNo",
                "gender",
                "licenseNo",
                "position"
        );
        verifyNoInteractions(staffService, notificationService);
    }

    @Test
    void duplicateMedicalLicenseReopensRegistrationWithALicenseFieldError() throws Exception {
        doThrow(new RuntimeException("Medical license number already exists."))
                .when(staffService)
                .registerStaff(any(), isNull());

        BindingResult errors = registrationFailureFor(
                "MEDICAL_STAFF",
                "APC-12345",
                "Nurse",
                "",
                ""
        );

        assertThat(errors.getFieldError("licenseNo").getDefaultMessage())
                .isEqualTo("Medical license number already exists.");
        verifyNoInteractions(notificationService);
    }

    @Test
    void duplicateLaboratoryCertificationReopensRegistrationWithACertificationFieldError() throws Exception {
        doThrow(new RuntimeException("Laboratory certification number already exists."))
                .when(staffService)
                .registerStaff(any(), isNull());

        BindingResult errors = registrationFailureFor(
                "LAB_TECHNICIAN",
                "",
                "",
                "LAB-2048",
                ""
        );

        assertThat(errors.getFieldError("certificationNo").getDefaultMessage())
                .isEqualTo("Laboratory certification number already exists.");
        verifyNoInteractions(notificationService);
    }

    private BindingResult registrationFailureFor(String role,
                                                 String licenseNo,
                                                 String position,
                                                 String certificationNo,
                                                 String department) throws Exception {
        Principal principal = () -> "admin.user";

        var result = mockMvc.perform(post("/admin/staff/register")
                        .principal(principal)
                        .param("fullName", "Aisyah Rahman")
                        .param("username", "aisyah.rahman")
                        .param("password", "SecurePassword123!")
                        .param("icNumber", "850101-10-2001")
                        .param("email", "aisyah@bloodbank.my")
                        .param("phoneNo", "012-3456789")
                        .param("gender", "FEMALE")
                        .param("staffType", role)
                        .param("licenseNo", licenseNo)
                        .param("position", position)
                        .param("certificationNo", certificationNo)
                        .param("department", department))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/staff/management"))
                .andExpect(flash().attribute("errorMessage", "Please correct the highlighted staff registration fields."))
                .andReturn();

        Object errors = result.getFlashMap().get(BindingResult.MODEL_KEY_PREFIX + "staffRequest");
        assertThat(errors).isInstanceOf(BindingResult.class);
        return (BindingResult) errors;
    }
}
