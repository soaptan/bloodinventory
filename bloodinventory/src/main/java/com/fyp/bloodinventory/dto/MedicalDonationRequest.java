package com.fyp.bloodinventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.ArrayList;
import java.util.List;

public class MedicalDonationRequest {
    @NotNull(message = "Please select a donor.")
    @Positive(message = "Please select a valid donor.")
    private Long donorId;

    @NotNull(message = "Please select a storage location.")
    @Positive(message = "Please select a valid storage location.")
    private Long locationId;

    @NotBlank(message = "Collection timestamp is required.")
    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?$",
            message = "Collection timestamp must be a valid date and time."
    )
    private String collectionTimestamp;

    @NotEmpty(message = "Please select at least one component type.")
    private List<@Pattern(
            regexp = "^(RBC|PLASMA|PLATELET)$",
            message = "Please select only supported blood component types."
    ) String> componentTypes = new ArrayList<>();

    public Long getDonorId() {
        return donorId;
    }

    public void setDonorId(Long donorId) {
        this.donorId = donorId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getCollectionTimestamp() {
        return collectionTimestamp;
    }

    public void setCollectionTimestamp(String collectionTimestamp) {
        this.collectionTimestamp = collectionTimestamp;
    }

    public List<String> getComponentTypes() {
        return componentTypes;
    }

    public void setComponentTypes(List<String> componentTypes) {
        this.componentTypes = componentTypes == null ? new ArrayList<>() : componentTypes;
    }
}
