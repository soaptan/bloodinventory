package com.fyp.bloodinventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "medical_staff")
@DiscriminatorValue("MEDICAL_STAFF")
@PrimaryKeyJoinColumn(name = "staff_id")
public class MedicalStaff extends Staff {

    @Column(name = "license_no", nullable = false, unique = true, length = 50)
    private String licenseNo;

    @Column(name = "position", nullable = false, length = 50)
    private String position;

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }
}
