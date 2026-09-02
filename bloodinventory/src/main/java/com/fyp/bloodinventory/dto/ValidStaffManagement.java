package com.fyp.bloodinventory.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = StaffManagementRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidStaffManagement {

    String message() default "Please correct the highlighted staff profile fields.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
