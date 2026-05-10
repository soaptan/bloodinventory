package com.fyp.bloodinventory.dto;

import java.sql.Timestamp;

public class MedicalTransfusionRecordDto {
    private Long componentId;
    private Long staffId;
    private String staffName;
    private Long patientId;
    private String patientName;
    private String condition;
    private Timestamp transfusionTimestamp;
    private String componentType;
    private String donorBloodGroup;

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
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

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public Timestamp getTransfusionTimestamp() {
        return transfusionTimestamp;
    }

    public void setTransfusionTimestamp(Timestamp transfusionTimestamp) {
        this.transfusionTimestamp = transfusionTimestamp;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public String getDonorBloodGroup() {
        return donorBloodGroup;
    }

    public void setDonorBloodGroup(String donorBloodGroup) {
        this.donorBloodGroup = donorBloodGroup;
    }
}
