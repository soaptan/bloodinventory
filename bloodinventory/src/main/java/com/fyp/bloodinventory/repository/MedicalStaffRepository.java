package com.fyp.bloodinventory.repository;

import com.fyp.bloodinventory.entity.MedicalStaff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalStaffRepository extends JpaRepository<MedicalStaff, Long> {
}