package com.fyp.bloodinventory.dto;

import com.fyp.bloodinventory.entity.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@ValidStaffManagement
public class StaffManagementRequest {

    private Long staffId;

    @NotBlank(message = "Full name is required.")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
    @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Full name contains invalid characters.")
    private String fullName;

    @NotBlank(message = "Username cannot be blank.")
    @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters.")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may contain only letters, numbers, dots, underscores, and hyphens.")
    private String username;

    @Size(max = 72, message = "Password must not exceed 72 characters.")
    @Pattern(
            regexp = "^$|^(?=.{8,72}$)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])\\S+$",
            message = "Password must include uppercase and lowercase letters, a number, and a special character, with no spaces."
    )
    private String password;

    @NotBlank(message = "Phone number is required.")
    @Size(max = 20, message = "Phone number must not exceed 20 characters.")
    @Pattern(regexp = "^(?:\\+?60|0)1\\d(?:[ -]?\\d){7,8}$", message = "Enter a valid Malaysian mobile number.")
    private String phoneNo;

    @NotBlank(message = "IC number is required.")
    @Pattern(regexp = "^\\d{6}-?\\d{2}-?\\d{4}$", message = "Enter a 12-digit IC number, for example 850101-10-2001.")
    private String icNumber;

    @NotBlank(message = "Gender must be selected.")
    @Pattern(regexp = "MALE|FEMALE|OTHER", message = "Please select a valid gender.")
    private String gender;

    @NotBlank(message = "Email is required.")
    @Email(message = "Please provide a valid email address.")
    @Size(max = 100, message = "Email must not exceed 100 characters.")
    private String email;

    @NotNull(message = "Role must be selected.")
    private StaffRole staffType;

    @Size(max = 50, message = "Medical license number must not exceed 50 characters.")
    @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 ./_-]*$", message = "Medical license number contains invalid characters.")
    private String licenseNo;

    @Size(max = 50, message = "Clinical position must not exceed 50 characters.")
    @Pattern(regexp = "^$|^[\\p{L}][\\p{L} .&'/-]*$", message = "Clinical position contains invalid characters.")
    private String position;

    @Size(max = 50, message = "Laboratory certification number must not exceed 50 characters.")
    @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 ./_-]*$", message = "Certification number contains invalid characters.")
    private String certificationNo;

    @Size(max = 100, message = "Department must not exceed 100 characters.")
    @Pattern(regexp = "^$|^[\\p{L}][\\p{L} .&'()/-]*$", message = "Department contains invalid characters.")
    private String department;

    @NotNull(message = "Account status must be selected.")
    private Boolean active;

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
