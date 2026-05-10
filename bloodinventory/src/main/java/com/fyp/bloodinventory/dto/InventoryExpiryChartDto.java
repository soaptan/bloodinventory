package com.fyp.bloodinventory.dto;

import java.time.LocalDate;

public class InventoryExpiryChartDto {

    private LocalDate expiryDate;
    private String status;
    private Long totalUnits;

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalUnits() {
        return totalUnits;
    }

    public void setTotalUnits(Long totalUnits) {
        this.totalUnits = totalUnits;
    }
}
