package com.fyp.bloodinventory.dto;

import java.sql.Timestamp;

public class LabComponentStatusDto {

    private Long componentId;
    private String componentType;
    private Timestamp expiryTimestamp;
    private String status;
    private Long donationId;
    private String donorName;
    private String donorBloodGroup;
    private String locationDescription;
    private String finalStatus;
    private String ttiScreening;
    private String bloodTypeMatch;

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

    public String getLocationDescription() {
        return locationDescription;
    }

    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }

    public String getTtiScreening() {
        return ttiScreening;
    }

    public void setTtiScreening(String ttiScreening) {
        this.ttiScreening = ttiScreening;
    }

    public String getBloodTypeMatch() {
        return bloodTypeMatch;
    }

    public void setBloodTypeMatch(String bloodTypeMatch) {
        this.bloodTypeMatch = bloodTypeMatch;
    }
}
