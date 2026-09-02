package com.fyp.bloodinventory.dto;

import java.util.List;

public final class StaffRegistrationRoleOptions {

    private static final List<String> CLINICAL_POSITIONS = List.of(
            "Medical Officer",
            "Doctor",
            "Nurse"
    );

    private static final List<String> ADMINISTRATOR_DEPARTMENTS = List.of(
            "System Administration",
            "Component Storage",
            "Inventory Control",
            "Eligibility Management",
            "Reporting and Audit"
    );

    private StaffRegistrationRoleOptions() {
    }

    public static List<String> clinicalPositions() {
        return CLINICAL_POSITIONS;
    }

    public static List<String> administratorDepartments() {
        return ADMINISTRATOR_DEPARTMENTS;
    }

    public static boolean isClinicalPosition(String value) {
        return CLINICAL_POSITIONS.contains(normalize(value));
    }

    public static boolean isAdministratorDepartment(String value) {
        return ADMINISTRATOR_DEPARTMENTS.contains(normalize(value));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
