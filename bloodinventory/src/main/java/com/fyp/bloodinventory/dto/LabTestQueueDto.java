package com.fyp.bloodinventory.dto;

import java.sql.Timestamp;

public class LabTestQueueDto {

    private Long donationId;
    private Timestamp collectionTimestamp;
    private String donorName;
    private String bloodGroup;
    private Long componentCount;
    private String componentTypes;
    private String componentStatuses;
    private Long testId;
    private String ttiScreening;
    private String bloodTypeMatch;
    private String finalStatus;
    private Timestamp testDate;
    private String staffName;

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

    public Long getComponentCount() {
        return componentCount;
    }

    public void setComponentCount(Long componentCount) {
        this.componentCount = componentCount;
    }

    public String getComponentTypes() {
        return componentTypes;
    }

    public void setComponentTypes(String componentTypes) {
        this.componentTypes = componentTypes;
    }

    public String getComponentStatuses() {
        return componentStatuses;
    }

    public void setComponentStatuses(String componentStatuses) {
        this.componentStatuses = componentStatuses;
    }

    public Long getTestId() {
        return testId;
    }

    public void setTestId(Long testId) {
        this.testId = testId;
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

    public String getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }

    public Timestamp getTestDate() {
        return testDate;
    }

    public void setTestDate(Timestamp testDate) {
        this.testDate = testDate;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }
}
