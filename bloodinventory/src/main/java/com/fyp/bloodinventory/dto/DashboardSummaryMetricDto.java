package com.fyp.bloodinventory.dto;

public class DashboardSummaryMetricDto {

    private String metricKey;
    private String metricLabel;
    private long metricValue;
    private String metricNote;
    private String metricColor;

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public String getMetricLabel() {
        return metricLabel;
    }

    public void setMetricLabel(String metricLabel) {
        this.metricLabel = metricLabel;
    }

    public long getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(long metricValue) {
        this.metricValue = metricValue;
    }

    public String getMetricNote() {
        return metricNote;
    }

    public void setMetricNote(String metricNote) {
        this.metricNote = metricNote;
    }

    public String getMetricColor() {
        return metricColor;
    }

    public void setMetricColor(String metricColor) {
        this.metricColor = metricColor;
    }
}
