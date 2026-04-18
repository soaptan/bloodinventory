package com.fyp.bloodinventory.repository;

import com.fyp.bloodinventory.entity.BloodAdministrator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloodAdministratorRepository extends JpaRepository<BloodAdministrator, Long> {
}