package com.fyp.bloodinventory.dto;

import com.fyp.bloodinventory.entity.StaffRole;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StaffRegistrationRequestValidator implements ConstraintValidator<ValidStaffRegistration, StaffRegistrationRequest> {

    private static final Pattern MEDICAL_LICENSE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ./_-]*$");
    private static final Pattern POSITION_PATTERN = Pattern.compile("^[\\p{L}][\\p{L} .&'/-]*$");
    private static final Pattern CERTIFICATION_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ./_-]*$");
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile("^[\\p{L}][\\p{L} .&'()/-]*$");

    @Override
    public boolean isValid(StaffRegistrationRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getStaffType() == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        if (request.getStaffType() == StaffRole.MEDICAL_STAFF) {
            valid &= requireValue(context, "licenseNo", request.getLicenseNo(),
                    "Medical license number is required.");
            valid &= requireApprovedValue(context, "position", request.getPosition(),
                    "Clinical position is required.",
                    "Select a valid clinical position.",
                    StaffRegistrationRoleOptions::isClinicalPosition);
            valid &= rejectUnexpectedValue(context, "certificationNo", request.getCertificationNo(),
                    CERTIFICATION_PATTERN, "Laboratory certification number is only used for lab technicians.");
            valid &= rejectUnexpectedValue(context, "department", request.getDepartment(),
                    DEPARTMENT_PATTERN, "Department is only used for blood administrators.");
            return valid;
        }

        if (request.getStaffType() == StaffRole.LAB_TECHNICIAN) {
            valid &= requireValue(context, "certificationNo", request.getCertificationNo(),
                    "Laboratory certification number is required.");
            valid &= rejectUnexpectedValue(context, "licenseNo", request.getLicenseNo(),
                    MEDICAL_LICENSE_PATTERN, "Medical license number is only used for medical staff.");
            valid &= rejectUnexpectedValue(context, "position", request.getPosition(),
                    POSITION_PATTERN, "Clinical position is only used for medical staff.");
            valid &= rejectUnexpectedValue(context, "department", request.getDepartment(),
                    DEPARTMENT_PATTERN, "Department is only used for blood administrators.");
            return valid;
        }

        if (request.getStaffType() == StaffRole.BLOOD_ADMINISTRATOR) {
            valid &= requireApprovedValue(context, "department", request.getDepartment(),
                    "Department is required.",
                    "Select a valid department.",
                    StaffRegistrationRoleOptions::isAdministratorDepartment);
            valid &= rejectUnexpectedValue(context, "licenseNo", request.getLicenseNo(),
                    MEDICAL_LICENSE_PATTERN, "Medical license number is only used for medical staff.");
            valid &= rejectUnexpectedValue(context, "position", request.getPosition(),
                    POSITION_PATTERN, "Clinical position is only used for medical staff.");
            valid &= rejectUnexpectedValue(context, "certificationNo", request.getCertificationNo(),
                    CERTIFICATION_PATTERN, "Laboratory certification number is only used for lab technicians.");
        }

        return valid;
    }

    private boolean requireValue(ConstraintValidatorContext context, String field, String value, String message) {
        if (!hasText(value)) {
            addViolation(context, field, message);
            return false;
        }

        return true;
    }

    private boolean requireApprovedValue(ConstraintValidatorContext context,
                                         String field,
                                         String value,
                                         String requiredMessage,
                                         String invalidMessage,
                                         java.util.function.Predicate<String> approvedValue) {
        if (!hasText(value)) {
            addViolation(context, field, requiredMessage);
            return false;
        }

        if (!approvedValue.test(value)) {
            addViolation(context, field, invalidMessage);
            return false;
        }

        return true;
    }

    private boolean rejectUnexpectedValue(ConstraintValidatorContext context,
                                          String field,
                                          String value,
                                          Pattern acceptedPattern,
                                          String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return true;
        }

        if (!acceptedPattern.matcher(normalized).matches()) {
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

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
