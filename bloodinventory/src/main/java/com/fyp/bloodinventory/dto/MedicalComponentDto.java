package com.fyp.bloodinventory.dto;

import java.sql.Timestamp;

public class MedicalComponentDto {
    private Long componentId;
    private String componentType;
    private Timestamp expiryTimestamp;
    private String status;
    private Long donationId;
    private Long locationId;
    private String locationDescription;
    private Long donorId;
    private String donorName;
    private String donorBloodGroup;
    private boolean compatible;
    private String compatibilityNote;

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public Timestamp getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public void setExpiryTimestamp(Timestamp expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDonationId() {
        return donationId;
    }

    public void setDonationId(Long donationId) {
        this.donationId = donationId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    public Long getDonorId() {
        return donorId;
    }

    public void setDonorId(Long donorId) {
        this.donorId = donorId;
    }

    public String getDonorName() {
        return donorName;
    }

    public void setDonorName(String donorName) {
        this.donorName = donorName;
    }

    public String getDonorBloodGroup() {
        return donorBloodGroup;
    }

    public void setDonorBloodGroup(String donorBloodGroup) {
        this.donorBloodGroup = donorBloodGroup;
    }

    public boolean isCompatible() {
        return compatible;
    }

    public void setCompatible(boolean compatible) {
        this.compatible = compatible;
    }

    public String getCompatibilityNote() {
        return compatibilityNote;
    }

    public void setCompatibilityNote(String compatibilityNote) {
        this.compatibilityNote = compatibilityNote;
    }
}
