package com.fyp.bloodinventory.dto;

import java.time.LocalDate;

public class DashboardOverviewTimelineDto {

    private LocalDate activityDate;
    private long donationCount;
    private long componentCount;
    private long availableCount;
    private long nearExpiryCount;
    private long selectedTotal;

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(LocalDate activityDate) {
        this.activityDate = activityDate;
    }

    public long getDonationCount() {
        return donationCount;
    }

    public void setDonationCount(long donationCount) {
        this.donationCount = donationCount;
    }

    public long getComponentCount() {
        return componentCount;
    }

    public void setComponentCount(long componentCount) {
        this.componentCount = componentCount;
    }

    public long getAvailableCount() {
        return availableCount;
    }

    public void setAvailableCount(long availableCount) {
        this.availableCount = availableCount;
    }

    public long getNearExpiryCount() {
        return nearExpiryCount;
    }

    public void setNearExpiryCount(long nearExpiryCount) {
        this.nearExpiryCount = nearExpiryCount;
    }

    public long getSelectedTotal() {
        return selectedTotal;
    }

    public void setSelectedTotal(long selectedTotal) {
        this.selectedTotal = selectedTotal;
    }
}
