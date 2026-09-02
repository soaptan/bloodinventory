package com.fyp.bloodinventory.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StaffRegistrationRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMalformedRegistrationIdentityAndContactFields() {
        StaffRegistrationRequest request = validRequest();
        request.setFullName("<script>");
        request.setUsername("bad username");
        request.setIcNumber("123");
        request.setEmail("not-an-email");
        request.setPhoneNo("abcdef");

        var invalidFields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(invalidFields).contains("fullName", "username", "icNumber", "email", "phoneNo");
    }

    @Test
    void acceptsSupportedMalaysianRegistrationFormats() {
        StaffRegistrationRequest request = validRequest();

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "lowercase1!",
            "UPPERCASE1!",
            "NoNumber!",
            "NoSymbol1",
            "Has Space1!"
    })
    void rejectsPasswordsThatDoNotMeetTheStaffAccountPolicy(String password) {
        StaffRegistrationRequest request = validRequest();
        request.setPassword(password);

        assertThat(invalidFields(request)).contains("password");
    }

    @Test
    void rejectsBlankPhoneNumber() {
        StaffRegistrationRequest request = validRequest();
        request.setPhoneNo("");

        assertThat(invalidFields(request)).contains("phoneNo");
    }

    @Test
    void rejectsMalformedMalaysianPhoneNumber() {
        StaffRegistrationRequest request = validRequest();
        request.setPhoneNo("011-123");

        assertThat(invalidFields(request)).contains("phoneNo");
    }

    @Test
    void acceptsMalaysianPhoneNumberWithCountryCode() {
        StaffRegistrationRequest request = validRequest();
        request.setPhoneNo("+6012-3456789");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMedicalStaffRoleFieldsThatAreMissingOrBelongToAnotherRole() {
        StaffRegistrationRequest request = validRequest();
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
    void rejectsLabTechnicianRoleFieldsThatAreMissingOrBelongToAnotherRole() {
        StaffRegistrationRequest request = validRequest();
        request.setStaffType(com.fyp.bloodinventory.entity.StaffRole.LAB_TECHNICIAN);
        request.setLicenseNo("APC-12345");
        request.setPosition("Senior Nurse");
        request.setCertificationNo("");
        request.setDepartment("Eligibility Management");

        assertThat(invalidFields(request)).contains(
                "licenseNo",
                "position",
                "certificationNo",
                "department"
        );
    }

    @Test
    void rejectsBloodAdministratorRoleFieldsThatAreMissingOrBelongToAnotherRole() {
        StaffRegistrationRequest request = validRequest();
        request.setStaffType(com.fyp.bloodinventory.entity.StaffRole.BLOOD_ADMINISTRATOR);
        request.setLicenseNo("APC-12345");
        request.setPosition("Senior Nurse");
        request.setCertificationNo("LAB-2048");
        request.setDepartment("");

        assertThat(invalidFields(request)).contains(
                "licenseNo",
                "position",
                "certificationNo",
                "department"
        );
    }

    @Test
    void rejectsClinicalPositionsOutsideTheApprovedRegistrationList() {
        StaffRegistrationRequest request = validRequest();
        request.setPosition("Paramedic");

        assertThat(invalidFields(request)).contains("position");
    }

    @Test
    void rejectsDepartmentsOutsideTheApprovedRegistrationList() {
        StaffRegistrationRequest request = validRequest();
        request.setStaffType(com.fyp.bloodinventory.entity.StaffRole.BLOOD_ADMINISTRATOR);
        request.setLicenseNo("");
        request.setPosition("");
        request.setDepartment("Finance");

        assertThat(invalidFields(request)).contains("department");
    }

    @Test
    void acceptsApprovedClinicalPositionsAndDepartments() {
        for (String position : new String[]{"Medical Officer", "Doctor", "Nurse"}) {
            StaffRegistrationRequest medicalRequest = validRequest();
            medicalRequest.setPosition(position);
            assertThat(validator.validate(medicalRequest)).isEmpty();
        }

        for (String department : new String[]{
                "System Administration",
                "Component Storage",
                "Inventory Control",
                "Eligibility Management",
                "Reporting and Audit"
        }) {
            StaffRegistrationRequest administratorRequest = validRequest();
            administratorRequest.setStaffType(com.fyp.bloodinventory.entity.StaffRole.BLOOD_ADMINISTRATOR);
            administratorRequest.setLicenseNo("");
            administratorRequest.setPosition("");
            administratorRequest.setDepartment(department);
            assertThat(validator.validate(administratorRequest)).isEmpty();
        }
    }

    private Set<String> invalidFields(StaffRegistrationRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private StaffRegistrationRequest validRequest() {
        StaffRegistrationRequest request = new StaffRegistrationRequest();
        request.setFullName("Aisyah Rahman");
        request.setUsername("aisyah.rahman");
        request.setPassword("SecurePassword123!");
        request.setIcNumber("850101-10-2001");
        request.setEmail("aisyah@bloodbank.my");
        request.setPhoneNo("012-3456789");
        request.setGender("FEMALE");
        request.setStaffType(com.fyp.bloodinventory.entity.StaffRole.MEDICAL_STAFF);
        request.setLicenseNo("APC-12345");
        request.setPosition("Nurse");
        return request;
    }
}
