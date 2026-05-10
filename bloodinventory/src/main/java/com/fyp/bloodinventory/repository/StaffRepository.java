package com.fyp.bloodinventory.repository;

import com.fyp.bloodinventory.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByUsernameAndStaffIdNot(String username, Long staffId);
    boolean existsByIcNumber(String icNumber);
    boolean existsByIcNumberAndStaffIdNot(String icNumber, Long staffId);
    boolean existsByEmail(String email);
    boolean existsByEmailAndStaffIdNot(String email, Long staffId);
}
