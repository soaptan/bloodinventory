package com.fyp.bloodinventory.dto;

import java.time.LocalDate;

public class MedicalDonorDto {
    private Long donorId;
    private String icNumber;
    private String fullName;
    private String bloodGroup;
    private LocalDate deferralExpiryDate;
    private String latestDeferralReason;
    private LocalDate latestDeferralDate;
    private String latestDeferralLockType;
    private boolean permanentDeferral;
    private boolean eligible;
    private String eligibilityStatus;

    public Long getDonorId() {
        return donorId;
    }

    public void setDonorId(Long donorId) {
        this.donorId = donorId;
    }

    public String getIcNumber() {
        return icNumber;
    }

    public void setIcNumber(String icNumber) {
        this.icNumber = icNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public LocalDate getDeferralExpiryDate() {
        return deferralExpiryDate;
    }

    public void setDeferralExpiryDate(LocalDate deferralExpiryDate) {
        this.deferralExpiryDate = deferralExpiryDate;
    }

    public String getLatestDeferralReason() {
        return latestDeferralReason;
    }

    public void setLatestDeferralReason(String latestDeferralReason) {
        this.latestDeferralReason = latestDeferralReason;
    }

    public LocalDate getLatestDeferralDate() {
        return latestDeferralDate;
    }

    public void setLatestDeferralDate(LocalDate latestDeferralDate) {
        this.latestDeferralDate = latestDeferralDate;
    }

    public String getLatestDeferralLockType() {
        return latestDeferralLockType;
    }

    public void setLatestDeferralLockType(String latestDeferralLockType) {
        this.latestDeferralLockType = latestDeferralLockType;
    }

    public boolean isPermanentDeferral() {
        return permanentDeferral;
    }

    public void setPermanentDeferral(boolean permanentDeferral) {
        this.permanentDeferral = permanentDeferral;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public String getEligibilityStatus() {
        return eligibilityStatus;
    }

    public void setEligibilityStatus(String eligibilityStatus) {
        this.eligibilityStatus = eligibilityStatus;
    }
}
