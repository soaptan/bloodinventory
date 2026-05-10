package com.fyp.bloodinventory.dto;

public class MedicalDashboardSummaryDto {
    private long eligibleDonors;
    private long deferredDonors;
    private long todayDonations;
    private long quarantinedComponents;
    private long availableComponents;
    private long transfusionEvents;

    public long getEligibleDonors() {
        return eligibleDonors;
    }

    public void setEligibleDonors(long eligibleDonors) {
        this.eligibleDonors = eligibleDonors;
    }

    public long getDeferredDonors() {
        return deferredDonors;
    }

    public void setDeferredDonors(long deferredDonors) {
        this.deferredDonors = deferredDonors;
    }

    public long getTodayDonations() {
        return todayDonations;
    }

    public void setTodayDonations(long todayDonations) {
        this.todayDonations = todayDonations;
    }

    public long getQuarantinedComponents() {
        return quarantinedComponents;
    }

    public void setQuarantinedComponents(long quarantinedComponents) {
        this.quarantinedComponents = quarantinedComponents;
    }

    public long getAvailableComponents() {
        return availableComponents;
    }

    public void setAvailableComponents(long availableComponents) {
        this.availableComponents = availableComponents;
    }

    public long getTransfusionEvents() {
        return transfusionEvents;
    }

    public void setTransfusionEvents(long transfusionEvents) {
        this.transfusionEvents = transfusionEvents;
    }
}
