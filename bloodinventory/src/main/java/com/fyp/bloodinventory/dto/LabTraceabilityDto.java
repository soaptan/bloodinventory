package com.fyp.bloodinventory.dto;

import java.sql.Timestamp;

public class LabTraceabilityDto {

    private Long componentId;
    private String componentType;
    private String componentStatus;
    private Long donationId;
    private Timestamp collectionTimestamp;
    private String donorName;
    private String donorBloodGroup;
    private String locationDescription;
    private Long testId;
    private String labFinalStatus;
    private Timestamp testDate;
    private String labStaffName;
    private Long patientId;
    private String patientName;
    private Timestamp transfusionTimestamp;
    private String transfusionStaffName;
    private String lifecycleStage;

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

    public String getComponentStatus() {
        return componentStatus;
    }

    public void setComponentStatus(String componentStatus) {
        this.componentStatus = componentStatus;
    }

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

    public Long getTestId() {
        return testId;
    }

    public void setTestId(Long testId) {
        this.testId = testId;
    }

    public String getLabFinalStatus() {
        return labFinalStatus;
    }

    public void setLabFinalStatus(String labFinalStatus) {
        this.labFinalStatus = labFinalStatus;
    }

    public Timestamp getTestDate() {
        return testDate;
    }

    public void setTestDate(Timestamp testDate) {
        this.testDate = testDate;
    }

    public String getLabStaffName() {
        return labStaffName;
    }

    public void setLabStaffName(String labStaffName) {
        this.labStaffName = labStaffName;
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
        this.patientName = patientName;
    }

    public Timestamp getTransfusionTimestamp() {
        return transfusionTimestamp;
    }

    public void setTransfusionTimestamp(Timestamp transfusionTimestamp) {
        this.transfusionTimestamp = transfusionTimestamp;
    }

    public String getTransfusionStaffName() {
        return transfusionStaffName;
    }

    public void setTransfusionStaffName(String transfusionStaffName) {
        this.transfusionStaffName = transfusionStaffName;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public void setLifecycleStage(String lifecycleStage) {
        this.lifecycleStage = lifecycleStage;
    }
}
