package com.fyp.bloodinventory.dto;

import com.fyp.bloodinventory.entity.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StaffRegistrationRequest {

    @NotBlank(message = "Full name is required.")
    @Size(max = 100, message = "Full name must not exceed 100 characters.")
    private String fullName;

    @NotBlank(message = "Username cannot be blank.")
    @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters.")
    private String username;

    @NotBlank(message = "Password cannot be blank.")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
    private String password;

    @Size(max = 20, message = "Phone number must not exceed 20 characters.")
    private String phoneNo;

    @NotBlank(message = "IC number is required.")
    @Size(max = 20, message = "IC number must not exceed 20 characters.")
    private String icNumber;

    @NotBlank(message = "Gender must be selected.")
    private String gender;

    @NotBlank(message = "Email is required.")
    @Email(message = "Please provide a valid email address.")
    @Size(max = 100, message = "Email must not exceed 100 characters.")
    private String email;

    @NotNull(message = "Role must be selected.")
    private StaffRole staffType;

    @Size(max = 50, message = "Medical license number must not exceed 50 characters.")
    private String licenseNo;

    @Size(max = 50, message = "Clinical position must not exceed 50 characters.")
    private String position;

    @Size(max = 50, message = "Laboratory certification number must not exceed 50 characters.")
    private String certificationNo;

    @Size(max = 100, message = "Department must not exceed 100 characters.")
    private String department;

    private String profilePhoto;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public String getIcNumber() {
        return icNumber;
    }

    public void setIcNumber(String icNumber) {
        this.icNumber = icNumber;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public StaffRole getStaffType() {
        return staffType;
    }

    public void setStaffType(StaffRole staffType) {
        this.staffType = staffType;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getCertificationNo() {
        return certificationNo;
    }

    public void setCertificationNo(String certificationNo) {
        this.certificationNo = certificationNo;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
    public String getProfilePhoto() {
    return profilePhoto;
    }

    public void setProfilePhoto(String profilePhoto) {
    this.profilePhoto = profilePhoto;
    }
}
