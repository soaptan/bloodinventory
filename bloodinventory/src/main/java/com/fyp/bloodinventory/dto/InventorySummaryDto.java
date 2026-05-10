package com.fyp.bloodinventory.dto;

public class InventorySummaryDto {

    private Long totalComponents;
    private Long availableComponents;
    private Long nearExpiryComponents;
    private Long totalDonations;

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

    public Long getTotalDonations() {
        return totalDonations;
    }

    public void setTotalDonations(Long totalDonations) {
        this.totalDonations = totalDonations;
    }
}