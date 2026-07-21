package com.fyp.bloodinventory.dto;

public class DeferralRuleRequest {

    private Long reasonId;
    private String description;
    private Integer defaultCoolingPeriodDays;
    private String lockType = "TEMPORARY";
    private Long staffId;

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

    public String getLockType() {
        return lockType;
    }

    public void setLockType(String lockType) {
        this.lockType = lockType;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }
}
