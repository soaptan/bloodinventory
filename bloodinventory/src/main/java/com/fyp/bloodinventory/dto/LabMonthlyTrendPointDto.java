package com.fyp.bloodinventory.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class LabMonthlyTrendPointDto {

    private LocalDate date;
    private long pendingCount;
    private long completedCount;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(long pendingCount) {
        this.pendingCount = pendingCount;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(long completedCount) {
        this.completedCount = completedCount;
    }

    public String getDateKey() {
        return date == null ? "" : date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public String getDateLabel() {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH));
    }
}
