package com.fyp.bloodinventory.dto;

public class LabScreeningRequest {

    private Long donationId;
    private String ttiScreening = "NEGATIVE";
    private String bloodTypeMatch = "MATCHED";
    private String finalStatus = "SAFE";

    public Long getDonationId() {
        return donationId;
    }

    public void setDonationId(Long donationId) {
        this.donationId = donationId;
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
}
