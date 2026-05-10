package com.fyp.bloodinventory.dto;

public class MedicalDeferralReasonDto {
    private Long reasonId;
    private String description;
    private Integer defaultCoolingPeriodDays;

    public Long getReasonId() {
        return reasonId;
    }

    public void setReasonId(Long reasonId) {
        this.reasonId = reasonId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDefaultCoolingPeriodDays() {
        return defaultCoolingPeriodDays;
    }

    public void setDefaultCoolingPeriodDays(Integer defaultCoolingPeriodDays) {
        this.defaultCoolingPeriodDays = defaultCoolingPeriodDays;
    }
}
