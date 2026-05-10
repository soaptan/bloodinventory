package com.fyp.bloodinventory.repository;

import com.fyp.bloodinventory.entity.MedicalStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicalStaffRepository extends JpaRepository<MedicalStaff, Long> {
    Optional<MedicalStaff> findByLicenseNo(String licenseNo);

    boolean existsByLicenseNo(String licenseNo);

    boolean existsByLicenseNoAndStaffIdNot(String licenseNo, Long staffId);
}
