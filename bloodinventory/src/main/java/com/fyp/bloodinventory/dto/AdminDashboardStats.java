package com.fyp.bloodinventory.dto;

public class AdminDashboardStats {

    private long totalStaff;
    private long totalDonors;
    private long totalDonations;
    private long totalBloodComponents;
    private long availableComponents;
    private long nearExpiryComponents;

    public long getTotalStaff() {
        return totalStaff;
    }

    public void setTotalStaff(long totalStaff) {
        this.totalStaff = totalStaff;
    }

    public long getTotalDonors() {
        return totalDonors;
    }

    public void setTotalDonors(long totalDonors) {
        this.totalDonors = totalDonors;
    }

    public long getTotalDonations() {
        return totalDonations;
    }

    public void setTotalDonations(long totalDonations) {
        this.totalDonations = totalDonations;
    }

    public long getTotalBloodComponents() {
        return totalBloodComponents;
    }

    public void setTotalBloodComponents(long totalBloodComponents) {
        this.totalBloodComponents = totalBloodComponents;
    }

    public long getAvailableComponents() {
        return availableComponents;
    }

    public void setAvailableComponents(long availableComponents) {
        this.availableComponents = availableComponents;
    }

    public long getNearExpiryComponents() {
        return nearExpiryComponents;
    }

    public void setNearExpiryComponents(long nearExpiryComponents) {
        this.nearExpiryComponents = nearExpiryComponents;
    }
}