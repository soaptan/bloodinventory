package com.fyp.bloodinventory.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalTransfusionRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidModeIdsPatientNameAndCondition() {
        MedicalTransfusionRequest request = validExistingRequest();
        request.setPatientMode("unknown");
        request.setPatientId(0L);
        request.setComponentId(-1L);
        request.setPatientName("Patient123");
        request.setCondition("x".repeat(201));

        Set<String> invalidFields = invalidFields(request);

        assertThat(invalidFields).contains("patientMode", "patientId", "componentId", "patientName", "condition");
    }

    @Test
    void acceptsAnExistingPatientRequest() {
        assertThat(validator.validate(validExistingRequest())).isEmpty();
    }

    @Test
    void acceptsANewPatientRequestWithAnOptionalCondition() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        request.setPatientMode("new");
        request.setPatientName("Nur A/P Ali");
        request.setCondition("Anaemia");
        request.setComponentId(8L);

        assertThat(validator.validate(request)).isEmpty();
    }

    private MedicalTransfusionRequest validExistingRequest() {
        MedicalTransfusionRequest request = new MedicalTransfusionRequest();
        request.setPatientMode("existing");
        request.setPatientId(17L);
        request.setComponentId(8L);
        return request;
    }

    private Set<String> invalidFields(MedicalTransfusionRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
