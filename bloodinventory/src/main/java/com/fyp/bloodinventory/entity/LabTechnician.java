package com.fyp.bloodinventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "lab_technician")
@DiscriminatorValue("LAB_TECHNICIAN")
@PrimaryKeyJoinColumn(name = "staff_id")
public class LabTechnician extends Staff {

    @Column(name = "certification_no", nullable = false, unique = true, length = 50)
    private String certificationNo;

    public String getCertificationNo() {
        return certificationNo;
    }

    public void setCertificationNo(String certificationNo) {
        this.certificationNo = certificationNo;
    }
}
