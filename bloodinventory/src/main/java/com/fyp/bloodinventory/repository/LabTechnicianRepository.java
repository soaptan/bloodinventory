package com.fyp.bloodinventory.repository;

import com.fyp.bloodinventory.entity.LabTechnician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LabTechnicianRepository extends JpaRepository<LabTechnician, Long> {
    Optional<LabTechnician> findByCertificationNo(String certificationNo);

    boolean existsByCertificationNo(String certificationNo);

    boolean existsByCertificationNoAndStaffIdNot(String certificationNo, Long staffId);
}
