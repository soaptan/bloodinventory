package com.fyp.bloodinventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "blood_administrator")
@DiscriminatorValue("BLOOD_ADMINISTRATOR")
@PrimaryKeyJoinColumn(name = "staff_id")
public class BloodAdministrator extends Staff {

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
