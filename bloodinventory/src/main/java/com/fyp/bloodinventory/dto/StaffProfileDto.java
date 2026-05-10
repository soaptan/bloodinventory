package com.fyp.bloodinventory.dto;

import com.fyp.bloodinventory.entity.StaffRole;

public class StaffProfileDto {

    private Long staffId;
    private String fullName;
    private String username;
    private String email;
    private String phoneNo;
    private String icNumber;
    private String gender;
    private String genderLabel;
    private StaffRole staffType;
    private String staffTypeLabel;
    private String roleAccentClass;
    private String photoUrl;
    private String initials;
    private String primaryDetailLabel;
    private String primaryDetailValue;
    private String secondaryDetailLabel;
    private String secondaryDetailValue;
    private String licenseNo;
    private String position;
    private String certificationNo;
    private String department;
    private Boolean active;
    private Boolean locked;
    private String statusLabel;
    private String statusAccentClass;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getGenderLabel() {
        return genderLabel;
    }

    public void setGenderLabel(String genderLabel) {
        this.genderLabel = genderLabel;
    }

    public StaffRole getStaffType() {
        return staffType;
    }

    public void setStaffType(StaffRole staffType) {
        this.staffType = staffType;
    }

    public String getStaffTypeLabel() {
        return staffTypeLabel;
    }

    public void setStaffTypeLabel(String staffTypeLabel) {
        this.staffTypeLabel = staffTypeLabel;
    }

    public String getRoleAccentClass() {
        return roleAccentClass;
    }

    public void setRoleAccentClass(String roleAccentClass) {
        this.roleAccentClass = roleAccentClass;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public String getPrimaryDetailLabel() {
        return primaryDetailLabel;
    }

    public void setPrimaryDetailLabel(String primaryDetailLabel) {
        this.primaryDetailLabel = primaryDetailLabel;
    }

    public String getPrimaryDetailValue() {
        return primaryDetailValue;
    }

    public void setPrimaryDetailValue(String primaryDetailValue) {
        this.primaryDetailValue = primaryDetailValue;
    }

    public String getSecondaryDetailLabel() {
        return secondaryDetailLabel;
    }

    public void setSecondaryDetailLabel(String secondaryDetailLabel) {
        this.secondaryDetailLabel = secondaryDetailLabel;
    }

    public String getSecondaryDetailValue() {
        return secondaryDetailValue;
    }

    public void setSecondaryDetailValue(String secondaryDetailValue) {
        this.secondaryDetailValue = secondaryDetailValue;
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

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public String getStatusAccentClass() {
        return statusAccentClass;
    }

    public void setStatusAccentClass(String statusAccentClass) {
        this.statusAccentClass = statusAccentClass;
    }
}
