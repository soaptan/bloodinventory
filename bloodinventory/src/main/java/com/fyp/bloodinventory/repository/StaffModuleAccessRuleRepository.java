package com.fyp.bloodinventory.repository;

import com.fyp.bloodinventory.entity.StaffModuleAccessRule;
import com.fyp.bloodinventory.entity.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffModuleAccessRuleRepository extends JpaRepository<StaffModuleAccessRule, Long> {
    List<StaffModuleAccessRule> findByStaffTypeAndEnabledTrueOrderBySortOrderAsc(StaffRole staffType);
}
