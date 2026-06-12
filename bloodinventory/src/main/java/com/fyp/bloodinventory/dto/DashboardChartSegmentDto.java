package com.fyp.bloodinventory.dto;

public class DashboardChartSegmentDto {

    private String label;
    private long value;
    private String tone;

    public DashboardChartSegmentDto(String label, long value, String tone) {
        this.label = label;
        this.value = value;
        this.tone = tone;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }
}
