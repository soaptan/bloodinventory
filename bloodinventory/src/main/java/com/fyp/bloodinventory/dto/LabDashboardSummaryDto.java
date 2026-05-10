package com.fyp.bloodinventory.dto;

public class LabDashboardSummaryDto {

    private long pendingTests;
    private long completedTests;
    private long safeComponents;
    private long discardedComponents;

    public long getPendingTests() {
        return pendingTests;
    }

    public void setPendingTests(long pendingTests) {
        this.pendingTests = pendingTests;
    }

    public long getCompletedTests() {
        return completedTests;
    }

    public void setCompletedTests(long completedTests) {
        this.completedTests = completedTests;
    }

    public long getSafeComponents() {
        return safeComponents;
    }

    public void setSafeComponents(long safeComponents) {
        this.safeComponents = safeComponents;
    }

    public long getDiscardedComponents() {
        return discardedComponents;
    }

    public void setDiscardedComponents(long discardedComponents) {
        this.discardedComponents = discardedComponents;
    }
}
