package com.fyp.bloodinventory.dto;

import com.fyp.bloodinventory.entity.StaffRole;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StaffManagementRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMalformedIdentityContactAndAccountFields() {
        StaffManagementRequest request = validRequest();
        request.setFullName("<script>");
        request.setUsername("bad username");
        request.setIcNumber("123");
        request.setEmail("not-an-email");
        request.setPhoneNo("abcdef");
        request.setGender("UNKNOWN");
        request.setStaffType(null);
        request.setActive(null);

        assertThat(invalidFields(request)).contains(
                "fullName",
                "username",
                "icNumber",
                "email",
                "phoneNo",
                "gender",
                "staffType",
                "active"
        );
    }

    @Test
    void acceptsAnEmptyReplacementPassword() {
        StaffManagementRequest request = validRequest();
        request.setPassword("");

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1!",
            "lowercase1!",
            "UPPERCASE1!",
            "NoNumber!",
            "NoSymbol1",
            "Has Space1!"
    })
    void rejectsReplacementPasswordsThatDoNotMeetPolicy(String password) {
        StaffManagementRequest request = validRequest();
        request.setPassword(password);

        assertThat(invalidFields(request)).contains("password");
    }

    @Test
    void acceptsAValidReplacementPassword() {
        StaffManagementRequest request = validRequest();
        request.setPassword("NewSecurePassword2!");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMedicalRoleDetailsThatAreMissingOrBelongToAnotherRole() {
        StaffManagementRequest request = validRequest();
        request.setLicenseNo("");
        request.setPosition("");
        request.setCertificationNo("LAB-2048");
        request.setDepartment("Eligibility Management");

        assertThat(invalidFields(request)).contains(
                "licenseNo",
                "position",
                "certificationNo",
                "department"
        );
    }

    @Test
    void rejectsLabRoleDetailsThatAreMissingOrBelongToAnotherRole() {
        StaffManagementRequest request = validRequest();
        request.setStaffType(StaffRole.LAB_TECHNICIAN);
        request.setLicenseNo("APC-12345");
        request.setPosition("Senior Nurse");
        request.setCertificationNo("");

        assertThat(invalidFields(request)).contains(
                "licenseNo",
                "position",
                "certificationNo"
        );
    }

    @Test
    void rejectsAdministratorRoleDetailsThatAreMissingOrBelongToAnotherRole() {
        StaffManagementRequest request = validRequest();
        request.setStaffType(StaffRole.BLOOD_ADMINISTRATOR);
        request.setLicenseNo("APC-12345");
        request.setPosition("Senior Nurse");
        request.setDepartment("");

        assertThat(invalidFields(request)).contains(
                "licenseNo",
                "position",
                "department"
        );
    }

    private Set<String> invalidFields(StaffManagementRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private StaffManagementRequest validRequest() {
        StaffManagementRequest request = new StaffManagementRequest();
        request.setFullName("Aisyah Rahman");
        request.setUsername("aisyah.rahman");
        request.setPassword("");
        request.setIcNumber("850101-10-2001");
        request.setEmail("aisyah@bloodbank.my");
        request.setPhoneNo("012-3456789");
        request.setGender("FEMALE");
        request.setStaffType(StaffRole.MEDICAL_STAFF);
        request.setLicenseNo("APC-12345");
        request.setPosition("Senior Nurse");
        request.setActive(true);
        return request;
    }
}
