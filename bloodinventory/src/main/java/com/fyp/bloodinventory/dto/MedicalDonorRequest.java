package com.fyp.bloodinventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class MedicalDonorRequest {
    @Positive(message = "Invalid donor record.")
    private Long donorId;

    @NotBlank(message = "Donor IC number is required.")
    @Pattern(
            regexp = "^(?:$|\\d{6}-\\d{2}-\\d{4})$",
            message = "Enter a valid Malaysian IC number, for example 900101-10-1234."
    )
    private String icNumber;

    @NotBlank(message = "Donor full name is required.")
    @Size(min = 2, max = 100, message = "Donor full name must be between 2 and 100 characters.")
    @Pattern(
            regexp = "^(?:$|[\\p{L}](?:[\\p{L}\\s.,'@/\\-])*)$",
            message = "Donor full name can only contain letters and common name punctuation."
    )
    private String fullName;

    @NotBlank(message = "Please select a blood group.")
    @Pattern(
            regexp = "^(?:$|(?:A|B|AB|O)[+-])$",
            message = "Please select a valid blood group."
    )
    private String bloodGroup;

    public Long getDonorId() {
        return donorId;
    }

    public void setDonorId(Long donorId) {
        this.donorId = donorId;
    }

    public String getIcNumber() {
        return icNumber;
    }

    public void setIcNumber(String icNumber) {
        this.icNumber = trim(icNumber);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = trim(fullName);
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = trim(bloodGroup);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
