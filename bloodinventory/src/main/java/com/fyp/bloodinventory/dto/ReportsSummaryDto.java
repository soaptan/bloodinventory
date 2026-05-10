package com.fyp.bloodinventory.dto;

public class ReportsSummaryDto {

    private Long totalStaff;
    private Long totalDonors;
    private Long totalDonations;
    private Long totalComponents;
    private Long availableComponents;
    private Long nearExpiryComponents;

    public Long getTotalStaff() {
        return totalStaff;
    }

    public void setTotalStaff(Long totalStaff) {
        this.totalStaff = totalStaff;
    }

    public Long getTotalDonors() {
        return totalDonors;
    }

    public void setTotalDonors(Long totalDonors) {
        this.totalDonors = totalDonors;
    }

    public Long getTotalDonations() {
        return totalDonations;
    }

    public void setTotalDonations(Long totalDonations) {
        this.totalDonations = totalDonations;
    }

    public Long getTotalComponents() {
        return totalComponents;
    }

    public void setTotalComponents(Long totalComponents) {
        this.totalComponents = totalComponents;
    }

    public Long getAvailableComponents() {
        return availableComponents;
    }

    public void setAvailableComponents(Long availableComponents) {
        this.availableComponents = availableComponents;
    }

    public Long getNearExpiryComponents() {
        return nearExpiryComponents;
    }

    public void setNearExpiryComponents(Long nearExpiryComponents) {
        this.nearExpiryComponents = nearExpiryComponents;
    }
}