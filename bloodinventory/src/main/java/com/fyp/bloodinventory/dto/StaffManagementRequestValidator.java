package com.fyp.bloodinventory.dto;

import com.fyp.bloodinventory.entity.StaffRole;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StaffManagementRequestValidator implements ConstraintValidator<ValidStaffManagement, StaffManagementRequest> {

    private static final Pattern MEDICAL_LICENSE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ./_-]*$");
    private static final Pattern POSITION_PATTERN = Pattern.compile("^[\\p{L}][\\p{L} .&'/-]*$");
    private static final Pattern CERTIFICATION_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ./_-]*$");
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile("^[\\p{L}][\\p{L} .&'()/-]*$");

    @Override
    public boolean isValid(StaffManagementRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getStaffType() == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        if (request.getStaffType() == StaffRole.MEDICAL_STAFF) {
            valid &= requireValue(context, "licenseNo", request.getLicenseNo(),
                    "Medical license number is required for medical staff.");
            valid &= requireValue(context, "position", request.getPosition(),
                    "Clinical position is required for medical staff.");
            valid &= rejectUnexpectedValue(context, "certificationNo", request.getCertificationNo(),
                    CERTIFICATION_PATTERN, "Laboratory certification number is only used for lab technicians.");
            valid &= rejectUnexpectedValue(context, "department", request.getDepartment(),
                    DEPARTMENT_PATTERN, "Department is only used for blood administrators.");
            return valid;
        }

        if (request.getStaffType() == StaffRole.LAB_TECHNICIAN) {
            valid &= requireValue(context, "certificationNo", request.getCertificationNo(),
                    "Laboratory certification number is required for lab technicians.");
            valid &= rejectUnexpectedValue(context, "licenseNo", request.getLicenseNo(),
                    MEDICAL_LICENSE_PATTERN, "Medical license number is only used for medical staff.");
            valid &= rejectUnexpectedValue(context, "position", request.getPosition(),
                    POSITION_PATTERN, "Clinical position is only used for medical staff.");
            valid &= rejectUnexpectedValue(context, "department", request.getDepartment(),
                    DEPARTMENT_PATTERN, "Department is only used for blood administrators.");
            return valid;
        }

        valid &= requireValue(context, "department", request.getDepartment(),
                "Department is required for blood administrators.");
        valid &= rejectUnexpectedValue(context, "licenseNo", request.getLicenseNo(),
                MEDICAL_LICENSE_PATTERN, "Medical license number is only used for medical staff.");
        valid &= rejectUnexpectedValue(context, "position", request.getPosition(),
                POSITION_PATTERN, "Clinical position is only used for medical staff.");
        valid &= rejectUnexpectedValue(context, "certificationNo", request.getCertificationNo(),
                CERTIFICATION_PATTERN, "Laboratory certification number is only used for lab technicians.");
        return valid;
    }

    private boolean requireValue(ConstraintValidatorContext context, String field, String value, String message) {
        if (trimToNull(value) != null) {
            return true;
        }

        addViolation(context, field, message);
        return false;
    }

    private boolean rejectUnexpectedValue(ConstraintValidatorContext context,
                                          String field,
                                          String value,
                                          Pattern acceptedPattern,
                                          String message) {
        String normalized = trimToNull(value);
        if (normalized == null || !acceptedPattern.matcher(normalized).matches()) {
            return true;
        }

        addViolation(context, field, message);
        return false;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
