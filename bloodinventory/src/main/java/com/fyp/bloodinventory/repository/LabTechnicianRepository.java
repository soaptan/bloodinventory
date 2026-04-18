package com.fyp.bloodinventory.repository;

import com.fyp.bloodinventory.entity.LabTechnician;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTechnicianRepository extends JpaRepository<LabTechnician, Long> {
}