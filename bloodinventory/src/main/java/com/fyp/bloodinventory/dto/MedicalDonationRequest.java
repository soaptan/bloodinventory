package com.fyp.bloodinventory.dto;

import java.util.ArrayList;
import java.util.List;

public class MedicalDonationRequest {
    private Long donorId;
    private Long locationId;
    private String collectionTimestamp;
    private List<String> componentTypes = new ArrayList<>();

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
