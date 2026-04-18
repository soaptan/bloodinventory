package com.fyp.bloodinventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "blood_administrator")
public class BloodAdministrator {

    @Id
    @Column(name = "staff_id")
    private Long staffId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public Staff getStaff() {
        return staff;
    }

    public void setStaff(Staff staff) {
        this.staff = staff;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}