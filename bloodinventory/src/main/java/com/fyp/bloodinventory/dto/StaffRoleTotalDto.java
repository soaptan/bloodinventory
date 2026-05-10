package com.fyp.bloodinventory.dto;

public class StaffRoleTotalDto {

    private String staffType;
    private Long totalStaff;

    public String getStaffType() {
        return staffType;
    }

    public void setStaffType(String staffType) {
        this.staffType = staffType;
    }

    public Long getTotalStaff() {
        return totalStaff;
    }

    public void setTotalStaff(Long totalStaff) {
        this.totalStaff = totalStaff;
    }
}