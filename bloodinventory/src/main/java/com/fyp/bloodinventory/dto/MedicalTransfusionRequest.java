package com.fyp.bloodinventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class MedicalTransfusionRequest {
    @NotBlank(message = "Please choose a patient type.")
    @Pattern(regexp = "^(existing|new)$", message = "Please choose a valid patient type.")
    private String patientMode;

    @Positive(message = "Please select a valid patient.")
    private Long patientId;

    @Size(max = 100, message = "Patient name must be 100 characters or fewer.")
    @Pattern(
            regexp = "^(?:$|[\\p{L}](?:[\\p{L}\\s.,'@/\\-])*)$",
            message = "Patient name can only contain letters and common name punctuation."
    )
    private String patientName;

    @Size(max = 200, message = "Patient condition must be 200 characters or fewer.")
    private String condition;

    @NotNull(message = "Please select a blood component.")
    @Positive(message = "Please select a valid blood component.")
    private Long componentId;

    public String getPatientMode() {
        return patientMode;
    }

    public void setPatientMode(String patientMode) {
        this.patientMode = trim(patientMode);
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = trim(patientName);
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = trim(condition);
    }

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
