package com.fyp.bloodinventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "medical_staff")
public class MedicalStaff {

    @Id
    @Column(name = "staff_id")
    private Long staffId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "license_no", nullable = false, unique = true, length = 50)
    private String licenseNo;

    @Column(name = "position", nullable = false, length = 50)
    private String position;

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