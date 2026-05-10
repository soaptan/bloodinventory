package com.fyp.bloodinventory.dto;

public class AvailableStockDto {

    private String componentType;
    private Long totalAvailable;

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public Long getTotalAvailable() {
        return totalAvailable;
    }

    public void setTotalAvailable(Long totalAvailable) {
        this.totalAvailable = totalAvailable;
    }
}