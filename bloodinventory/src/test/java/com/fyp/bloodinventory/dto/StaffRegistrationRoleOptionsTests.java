package com.fyp.bloodinventory.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaffRegistrationRoleOptionsTests {

    @Test
    void exposesTheApprovedRegistrationRoleOptions() {
        assertThat(StaffRegistrationRoleOptions.clinicalPositions())
                .containsExactly("Medical Officer", "Doctor", "Nurse");
        assertThat(StaffRegistrationRoleOptions.administratorDepartments())
                .containsExactly(
                        "System Administration",
                        "Component Storage",
                        "Inventory Control",
                        "Eligibility Management",
                        "Reporting and Audit"
                );
    }
}
