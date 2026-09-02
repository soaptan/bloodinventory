package com.fyp.bloodinventory.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalDonorRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMalformedDonorIdentityFields() {
        MedicalDonorRequest request = validDonorRequest();
        request.setFullName("John123");
        request.setIcNumber("990101100001");
        request.setBloodGroup("X+");

        assertThat(invalidFields(request)).contains("fullName", "icNumber", "bloodGroup");
    }

    @Test
    void acceptsACompleteDonorIdentity() {
        assertThat(validator.validate(validDonorRequest())).isEmpty();
    }

    @Test
    void rejectsMissingOrNonPositiveDeferralSelections() {
        MedicalDeferralRequest request = new MedicalDeferralRequest();
        request.setDonorId(0L);
        request.setReasonId(null);

        assertThat(invalidFields(request)).contains("donorId", "reasonId");
    }

    @Test
    void acceptsSelectedDonorAndDeferralReason() {
        MedicalDeferralRequest request = new MedicalDeferralRequest();
        request.setDonorId(12L);
        request.setReasonId(5L);

        assertThat(validator.validate(request)).isEmpty();
    }

    private MedicalDonorRequest validDonorRequest() {
        MedicalDonorRequest request = new MedicalDonorRequest();
        request.setFullName("Nur A/P Ali");
        request.setIcNumber("900101-10-1234");
        request.setBloodGroup("AB+");
        return request;
    }

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
