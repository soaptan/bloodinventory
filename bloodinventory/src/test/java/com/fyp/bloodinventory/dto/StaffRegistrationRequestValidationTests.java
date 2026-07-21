package com.fyp.bloodinventory.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

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

    private StaffRegistrationRequest validRequest() {
        StaffRegistrationRequest request = new StaffRegistrationRequest();
        request.setFullName("Aisyah Rahman");
        request.setUsername("aisyah.rahman");
        request.setPassword("securePassword123");
        request.setIcNumber("850101-10-2001");
        request.setEmail("aisyah@bloodbank.my");
        request.setPhoneNo("012-3456789");
        request.setGender("FEMALE");
        request.setStaffType(com.fyp.bloodinventory.entity.StaffRole.MEDICAL_STAFF);
        request.setLicenseNo("APC-12345");
        request.setPosition("Senior Nurse");
        return request;
    }
}
