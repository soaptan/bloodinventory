package com.fyp.bloodinventory.dto;

import java.sql.Timestamp;

public class MedicalDonationDto {
    private Long donationId;
    private Timestamp collectionTimestamp;
    private Long donorId;
    private String donorName;
    private String bloodGroup;
    private Long staffId;
    private String staffName;
    private Long locationId;
    private String locationDescription;
    private long componentCount;
    private String componentStatuses;

    public Long getDonationId() {
        return donationId;
    }

    public void setDonationId(Long donationId) {
        this.donationId = donationId;
    }

    public Timestamp getCollectionTimestamp() {
        return collectionTimestamp;
    }

    public void setCollectionTimestamp(Timestamp collectionTimestamp) {
        this.collectionTimestamp = collectionTimestamp;
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

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
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

    public long getComponentCount() {
        return componentCount;
    }

    public void setComponentCount(long componentCount) {
        this.componentCount = componentCount;
    }

    public String getComponentStatuses() {
        return componentStatuses;
    }

    public void setComponentStatuses(String componentStatuses) {
        this.componentStatuses = componentStatuses;
    }
}
