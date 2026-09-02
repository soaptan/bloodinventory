package com.fyp.bloodinventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class MedicalDeferralRequest {
    @NotNull(message = "Please select a donor.")
    @Positive(message = "Please select a valid donor.")
    private Long donorId;

    @NotNull(message = "Please select a deferral reason.")
    @Positive(message = "Please select a valid deferral reason.")
    private Long reasonId;

    public Long getDonorId() {
        return donorId;
    }

    public void setDonorId(Long donorId) {
        this.donorId = donorId;
    }

    public Long getReasonId() {
        return reasonId;
    }

    public void setReasonId(Long reasonId) {
        this.reasonId = reasonId;
    }
}
