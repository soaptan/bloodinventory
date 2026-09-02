package com.fyp.bloodinventory.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalDonationRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNonPositiveIdsAndUnsupportedComponentTypes() {
        MedicalDonationRequest request = validRequest();
        request.setDonorId(0L);
        request.setLocationId(-1L);
        request.setComponentTypes(List.of("RBC", "UNSUPPORTED"));

        Set<String> invalidFields = invalidFields(request);

        assertThat(invalidFields).contains("donorId", "locationId");
        assertThat(invalidFields).anyMatch(field -> field.startsWith("componentTypes"));
    }

    @Test
    void acceptsACompleteDonationRequestWithApprovedComponentTypes() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    private MedicalDonationRequest validRequest() {
        MedicalDonationRequest request = new MedicalDonationRequest();
        request.setDonorId(12L);
        request.setLocationId(4L);
        request.setCollectionTimestamp(LocalDateTime.now().minusMinutes(2).withNano(0).toString());
        request.setComponentTypes(List.of("RBC", "PLASMA"));
        return request;
    }

    private Set<String> invalidFields(MedicalDonationRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
