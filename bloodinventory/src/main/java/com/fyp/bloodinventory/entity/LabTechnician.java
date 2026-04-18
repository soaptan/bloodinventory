package com.fyp.bloodinventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "lab_technician")
public class LabTechnician {

    @Id
    @Column(name = "staff_id")
    private Long staffId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "certification_no", nullable = false, unique = true, length = 50)
    private String certificationNo;

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

    public String getCertificationNo() {
        return certificationNo;
    }

    public void setCertificationNo(String certificationNo) {
        this.certificationNo = certificationNo;
    }
}