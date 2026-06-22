package com.fyp.bloodinventory.dto;

public class AuditTrailSummaryDto {

    private long totalEvents;
    private long recentEvents;
    private long componentEvents;
    private long activeActors;

    public long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public long getRecentEvents() {
        return recentEvents;
    }

    public void setRecentEvents(long recentEvents) {
        this.recentEvents = recentEvents;
    }

    public long getComponentEvents() {
        return componentEvents;
    }

    public void setComponentEvents(long componentEvents) {
        this.componentEvents = componentEvents;
    }

    public long getActiveActors() {
        return activeActors;
    }

    public void setActiveActors(long activeActors) {
        this.activeActors = activeActors;
    }
}
