package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.config.PasswordHashSupport;
import com.fyp.bloodinventory.dto.StaffManagementRequest;
import com.fyp.bloodinventory.dto.StaffProfileDto;
import com.fyp.bloodinventory.dto.StaffProfileUpdateRequest;
import com.fyp.bloodinventory.dto.StaffRegistrationRequest;
import com.fyp.bloodinventory.entity.BloodAdministrator;
import com.fyp.bloodinventory.entity.LabTechnician;
import com.fyp.bloodinventory.entity.MedicalStaff;
import com.fyp.bloodinventory.entity.Staff;
import com.fyp.bloodinventory.entity.StaffRole;
import com.fyp.bloodinventory.repository.BloodAdministratorRepository;
import com.fyp.bloodinventory.repository.LabTechnicianRepository;
import com.fyp.bloodinventory.repository.MedicalStaffRepository;
import com.fyp.bloodinventory.repository.StaffRepository;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class StaffService {

    private static final String DEFAULT_PHOTO = "staff/default.png";
    private static final String LEGACY_DEFAULT_PHOTO = "default.png";
    private static final DateTimeFormatter SESSION_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StaffRepository staffRepository;
    private final MedicalStaffRepository medicalStaffRepository;
    private final LabTechnicianRepository labTechnicianRepository;
    private final BloodAdministratorRepository bloodAdministratorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseAuditContextService auditContextService;

    public StaffService(StaffRepository staffRepository,
                        MedicalStaffRepository medicalStaffRepository,
                        LabTechnicianRepository labTechnicianRepository,
                        BloodAdministratorRepository bloodAdministratorRepository,
                        PasswordEncoder passwordEncoder,
                        JdbcTemplate jdbcTemplate,
                        DatabaseAuditContextService auditContextService) {
        this.staffRepository = staffRepository;
        this.medicalStaffRepository = medicalStaffRepository;
        this.labTechnicianRepository = labTechnicianRepository;
        this.bloodAdministratorRepository = bloodAdministratorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.auditContextService = auditContextService;
    }

    @Transactional
    public void registerStaff(StaffRegistrationRequest request, MultipartFile photoFile) throws Exception {
        applyAuditContext();
        StaffRole staffType = requireStaffType(request.getStaffType());
        String fullName = requireText(request.getFullName(), "Please enter the staff member's full name.");
        String username = requireText(request.getUsername(), "Please enter a username.");
        String password = requirePassword(request.getPassword(), true);
        String icNumber = requireText(request.getIcNumber(), "Please enter the IC number.");
        String gender = requireText(request.getGender(), "Please select a gender.");
        String email = trimToNull(request.getEmail());
        String phoneNo = trimToNull(request.getPhoneNo());

        validateAccountUniqueness(username, icNumber, email, null);
        validateRoleSpecificDetails(staffType, request.getLicenseNo(), request.getPosition(),
                request.getCertificationNo(), request.getDepartment(), null);

        Staff staff = newStaffForRole(staffType, request.getLicenseNo(), request.getPosition(),
                request.getCertificationNo(), request.getDepartment());
        staff.setFullName(fullName);
        staff.setUsername(username);
        staff.setPassword(prepareStoredPassword(password));
        staff.setPhoneNo(phoneNo);
        staff.setIcNumber(icNumber);
        staff.setGender(gender.toUpperCase());
        staff.setEmail(email);
        staff.setActive(Boolean.TRUE);
        staff.setLocked(Boolean.FALSE);
        staff.setProfilePhoto(storeProfilePhoto(photoFile, username));

        staffRepository.save(staff);
    }

    public List<StaffProfileDto> getAllStaffProfiles() {
        List<Staff> staffList = staffRepository.findAll(Sort.by(Sort.Direction.ASC, "fullName"));
        List<StaffProfileDto> profiles = new ArrayList<>();

        for (Staff staff : staffList) {
            profiles.add(buildStaffProfile(staff));
        }

        return profiles;
    }

    public StaffProfileDto getStaffProfileByUsername(@NonNull String username) {
        Staff staff = findStaffByUsername(username);
        return buildStaffProfile(staff);
    }

    public Long getStaffIdByUsername(@NonNull String username) {
        Staff staff = findStaffByUsername(username);
        return requireStaffId(staff);
    }

    public StaffProfileUpdateRequest getProfileUpdateRequestByUsername(@NonNull String username) {
        Staff staff = findStaffByUsername(username);
        StaffProfileUpdateRequest request = new StaffProfileUpdateRequest();

        request.setFullName(emptyText(staff.getFullName()));
        request.setPhoneNo(emptyText(staff.getPhoneNo()));
        request.setEmail(emptyText(staff.getEmail()));
        request.setGender(emptyText(staff.getGender()));
        return request;
    }

    @Transactional
    public void updateOwnProfile(@NonNull String username, StaffProfileUpdateRequest request) {
        applyAuditContext();
        Staff staff = findStaffByUsername(username);

        String fullName = requireText(request.getFullName(), "Please enter your full name.");
        String email = requireText(request.getEmail(), "Please enter your email address.");
        String gender = requireText(request.getGender(), "Please select your gender.");
        String phoneNo = trimToNull(request.getPhoneNo());

        String currentEmail = emptyText(staff.getEmail());
        if (!currentEmail.equalsIgnoreCase(email) && staffRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists.");
        }

        staff.setFullName(fullName);
        staff.setEmail(email);
        staff.setPhoneNo(phoneNo);
        staff.setGender(gender.toUpperCase());
        staffRepository.save(staff);
    }

    @Transactional
    public void updateOwnPassword(@NonNull String username,
                                  String currentPassword,
                                  String newPassword,
                                  String confirmPassword) {
        applyAuditContext();
        Staff staff = findStaffByUsername(username);

        String normalizedCurrentPassword = requireText(currentPassword, "Please enter your current password.");
        String normalizedNewPassword = requirePassword(newPassword, true);
        String normalizedConfirmPassword = requirePassword(confirmPassword, true);

        if (!normalizedNewPassword.equals(normalizedConfirmPassword)) {
            throw new RuntimeException("New password and confirmation do not match.");
        }

        String storedPassword = PasswordHashSupport.normalizeStoredPassword(staff.getPassword());
        if (!passwordEncoder.matches(normalizedCurrentPassword, storedPassword)) {
            throw new RuntimeException("Current password is incorrect.");
        }

        staff.setPassword(prepareStoredPassword(normalizedNewPassword));
        staffRepository.save(staff);
    }

    @Transactional
    public void updateStaff(@NonNull Long staffId, StaffManagementRequest request, String currentUsername) {
        applyAuditContext();
        Long requiredStaffId = Objects.requireNonNull(staffId, "Staff ID must not be null.");
        Staff staff = findStaffById(requiredStaffId);

        StaffRole staffType = requireStaffType(request.getStaffType());
        String fullName = requireText(request.getFullName(), "Please enter the staff member's full name.");
        String username = requireText(request.getUsername(), "Please enter a username.");
        String icNumber = requireText(request.getIcNumber(), "Please enter the IC number.");
        String gender = requireText(request.getGender(), "Please select a gender.");
        String email = trimToNull(request.getEmail());
        String phoneNo = trimToNull(request.getPhoneNo());
        boolean active = !Boolean.FALSE.equals(request.getActive());

        boolean editingOwnAccount = isCurrentUser(staff, currentUsername);
        if (editingOwnAccount) {
            if (!staff.getUsername().equalsIgnoreCase(username)) {
                throw new RuntimeException("Use My Profile to change your own username.");
            }

            if (staff.getStaffType() != staffType) {
                throw new RuntimeException("Use another administrator account to change your own role.");
            }

            if (!active) {
                throw new RuntimeException("You cannot deactivate your own account from staff management.");
            }
        }

        validateAccountUniqueness(username, icNumber, email, requiredStaffId);
        validateRoleSpecificDetails(staffType, request.getLicenseNo(), request.getPosition(),
                request.getCertificationNo(), request.getDepartment(), requiredStaffId);

        String password = trimToNull(request.getPassword());
        String storedPassword = password == null
                ? staff.getPassword()
                : prepareStoredPassword(requirePassword(password, false));

        if (staff.getStaffType() != staffType) {
            replaceStaffSubtype(requiredStaffId, staffType, fullName, username, phoneNo, icNumber,
                    gender.toUpperCase(), email, storedPassword, request.getLicenseNo(), request.getPosition(),
                    request.getCertificationNo(), request.getDepartment(), currentUsername);
            applyAccountStatus(requiredStaffId, active);
            return;
        }

        staff.setFullName(fullName);
        staff.setUsername(username);
        staff.setPhoneNo(phoneNo);
        staff.setIcNumber(icNumber);
        staff.setGender(gender.toUpperCase());
        staff.setEmail(email);
        staff.setPassword(storedPassword);
        applyRoleSpecificDetails(staff, staffType, request.getLicenseNo(), request.getPosition(),
                request.getCertificationNo(), request.getDepartment());

        staffRepository.save(staff);
        applyAccountStatus(requiredStaffId, active);
    }

    @Transactional
    public void deleteStaff(@NonNull Long staffId, String currentUsername) {
        applyAuditContext();
        Long requiredStaffId = Objects.requireNonNull(staffId, "Staff ID must not be null.");
        Staff staff = findStaffById(requiredStaffId);

        if (isCurrentUser(staff, currentUsername)) {
            throw new RuntimeException("You cannot delete your own account from staff management.");
        }

        deleteStaffRecord(staff);
    }

    @Transactional
    public int deleteSelectedStaff(List<Long> staffIds, String currentUsername) {
        applyAuditContext();
        List<Long> uniqueStaffIds = uniqueStaffIds(staffIds);
        if (uniqueStaffIds.isEmpty()) {
            throw new RuntimeException("Select at least one staff record to delete.");
        }

        List<Staff> selectedStaff = new ArrayList<>();
        for (Long selectedStaffId : uniqueStaffIds) {
            Long requiredStaffId = Objects.requireNonNull(selectedStaffId, "Staff ID must not be null.");
            selectedStaff.add(findStaffById(requiredStaffId));
        }

        for (Staff staff : selectedStaff) {
            if (isCurrentUser(staff, currentUsername)) {
                throw new RuntimeException("Deselect your own account before deleting staff records.");
            }
        }

        selectedStaff.forEach(this::deleteStaffRecord);
        return selectedStaff.size();
    }

    @Transactional
    public void updateStaffPhoto(@NonNull Long staffId, MultipartFile photoFile) throws Exception {
        applyAuditContext();
        Long requiredStaffId = Objects.requireNonNull(staffId, "Staff ID must not be null.");
        Staff staff = findStaffById(requiredStaffId);
        updateProfilePhoto(staff, photoFile);
    }

    @Transactional
    public void updateStaffPhotoByUsername(@NonNull String username, MultipartFile photoFile) throws Exception {
        applyAuditContext();
        Staff staff = findStaffByUsername(username);
        updateProfilePhoto(staff, photoFile);
    }

    private void updateProfilePhoto(Staff staff, MultipartFile photoFile) throws Exception {
        Long staffId = requireStaffId(staff);

        if (photoFile == null || photoFile.isEmpty()) {
            throw new RuntimeException("Please select an image file.");
        }

        String newFileName = storeUploadedFile(photoFile, "staff_" + staffId);
        deleteProfilePhotoFile(staff.getProfilePhoto());

        staff.setProfilePhoto("staff/" + newFileName);
        staffRepository.save(staff);
    }

    private void applyAuditContext() {
        auditContextService.applyCurrentContext();
    }

    private StaffProfileDto buildStaffProfile(Staff staff) {
        Long staffId = requireStaffId(staff);
        StaffProfileDto profile = new StaffProfileDto();

        profile.setStaffId(staffId);
        profile.setFullName(defaultText(staff.getFullName()));
        profile.setUsername(defaultText(staff.getUsername()));
        profile.setEmail(defaultText(staff.getEmail()));
        profile.setPhoneNo(defaultText(staff.getPhoneNo()));
        profile.setIcNumber(defaultText(staff.getIcNumber()));
        profile.setGender(emptyText(staff.getGender()));
        profile.setGenderLabel(formatTextValue(staff.getGender()));
        profile.setStaffType(staff.getStaffType());
        profile.setInitials(buildInitials(staff.getFullName()));
        profile.setActive(!Boolean.FALSE.equals(staff.getActive()));
        profile.setLocked(Boolean.TRUE.equals(staff.getLocked()));
        profile.setLastLoginDisplay(lastLoginDisplay(staff.getUsername()));

        if (!profile.getActive()) {
            profile.setStatusLabel("Inactive");
            profile.setStatusAccentClass("inactive");
        } else if (profile.getLocked()) {
            profile.setStatusLabel("Locked");
            profile.setStatusAccentClass("locked");
        } else {
            profile.setStatusLabel("Active");
            profile.setStatusAccentClass("active");
        }

        profile.setPhotoUrl(resolveProfilePhotoUrl(staff.getProfilePhoto()));

        if (staff.getStaffType() == StaffRole.BLOOD_ADMINISTRATOR) {
            profile.setStaffTypeLabel("Blood Administrator");
            profile.setRoleAccentClass("administrator");

            bloodAdministratorRepository.findById(staffId).ifPresent(admin -> {
                profile.setDepartment(emptyText(admin.getDepartment()));
                profile.setPrimaryDetailLabel("Department");
                profile.setPrimaryDetailValue(defaultText(admin.getDepartment()));
            });
        } else if (staff.getStaffType() == StaffRole.MEDICAL_STAFF) {
            profile.setStaffTypeLabel("Medical Staff");
            profile.setRoleAccentClass("medical");

            medicalStaffRepository.findById(staffId).ifPresent(medical -> {
                profile.setLicenseNo(emptyText(medical.getLicenseNo()));
                profile.setPosition(emptyText(medical.getPosition()));
                profile.setPrimaryDetailLabel("License No");
                profile.setPrimaryDetailValue(defaultText(medical.getLicenseNo()));
                profile.setSecondaryDetailLabel("Position");
                profile.setSecondaryDetailValue(defaultText(medical.getPosition()));
            });
        } else if (staff.getStaffType() == StaffRole.LAB_TECHNICIAN) {
            profile.setStaffTypeLabel("Lab Technician");
            profile.setRoleAccentClass("lab");

            labTechnicianRepository.findById(staffId).ifPresent(lab -> {
                profile.setCertificationNo(emptyText(lab.getCertificationNo()));
                profile.setPrimaryDetailLabel("Certification No");
                profile.setPrimaryDetailValue(defaultText(lab.getCertificationNo()));
            });
        } else {
            profile.setStaffTypeLabel("Staff");
            profile.setRoleAccentClass("lab");
        }

        return profile;
    }

    private String lastLoginDisplay(String username) {
        List<Timestamp> lastSeen = jdbcTemplate.queryForList("""
                SELECT last_seen_at
                FROM staff_login_session
                WHERE LOWER(username) = LOWER(?)
                ORDER BY last_seen_at DESC
                LIMIT 1
                """, Timestamp.class, username);

        if (lastSeen.isEmpty() || lastSeen.get(0) == null) {
            return "No session recorded";
        }

        return lastSeen.get(0).toLocalDateTime().format(SESSION_TIMESTAMP_FORMAT);
    }

    private void validateAccountUniqueness(String username, String icNumber, String email, Long staffId) {
        boolean duplicateUsername = staffId == null
                ? staffRepository.existsByUsername(username)
                : staffRepository.existsByUsernameAndStaffIdNot(username, staffId);
        if (duplicateUsername) {
            throw new RuntimeException("Username already exists.");
        }

        boolean duplicateIcNumber = staffId == null
                ? staffRepository.existsByIcNumber(icNumber)
                : staffRepository.existsByIcNumberAndStaffIdNot(icNumber, staffId);
        if (duplicateIcNumber) {
            throw new RuntimeException("IC number already exists.");
        }

        if (email != null) {
            boolean duplicateEmail = staffId == null
                    ? staffRepository.existsByEmail(email)
                    : staffRepository.existsByEmailAndStaffIdNot(email, staffId);
            if (duplicateEmail) {
                throw new RuntimeException("Email already exists.");
            }
        }
    }

    private void validateRoleSpecificDetails(StaffRole staffType,
                                             String licenseNo,
                                             String position,
                                             String certificationNo,
                                             String department,
                                             Long staffId) {
        if (staffType == StaffRole.MEDICAL_STAFF) {
            String normalizedLicenseNo = requireText(licenseNo, "Please enter the medical license number.");
            requireText(position, "Please enter the medical staff position.");

            boolean duplicateLicenseNo = staffId == null
                    ? medicalStaffRepository.existsByLicenseNo(normalizedLicenseNo)
                    : medicalStaffRepository.existsByLicenseNoAndStaffIdNot(normalizedLicenseNo, staffId);
            if (duplicateLicenseNo) {
                throw new RuntimeException("Medical license number already exists.");
            }
            return;
        }

        if (staffType == StaffRole.LAB_TECHNICIAN) {
            String normalizedCertificationNo = requireText(certificationNo,
                    "Please enter the laboratory certification number.");

            boolean duplicateCertificationNo = staffId == null
                    ? labTechnicianRepository.existsByCertificationNo(normalizedCertificationNo)
                    : labTechnicianRepository.existsByCertificationNoAndStaffIdNot(normalizedCertificationNo, staffId);
            if (duplicateCertificationNo) {
                throw new RuntimeException("Laboratory certification number already exists.");
            }
            return;
        }

        requireText(department, "Please enter the administrator department.");
    }

    private Staff newStaffForRole(StaffRole staffType,
                                  String licenseNo,
                                  String position,
                                  String certificationNo,
                                  String department) {
        if (staffType == StaffRole.MEDICAL_STAFF) {
            MedicalStaff medicalStaff = new MedicalStaff();
            medicalStaff.setLicenseNo(requireText(licenseNo, "Please enter the medical license number."));
            medicalStaff.setPosition(requireText(position, "Please enter the medical staff position."));
            return medicalStaff;
        }

        if (staffType == StaffRole.LAB_TECHNICIAN) {
            LabTechnician labTechnician = new LabTechnician();
            labTechnician.setCertificationNo(requireText(certificationNo,
                    "Please enter the laboratory certification number."));
            return labTechnician;
        }

        BloodAdministrator administrator = new BloodAdministrator();
        administrator.setDepartment(requireText(department, "Please enter the administrator department."));
        return administrator;
    }

    private void applyRoleSpecificDetails(Staff staff,
                                          StaffRole staffType,
                                          String licenseNo,
                                          String position,
                                          String certificationNo,
                                          String department) {
        if (staffType == StaffRole.MEDICAL_STAFF && staff instanceof MedicalStaff medicalStaff) {
            medicalStaff.setLicenseNo(requireText(licenseNo, "Please enter the medical license number."));
            medicalStaff.setPosition(requireText(position, "Please enter the medical staff position."));
            return;
        }

        if (staffType == StaffRole.LAB_TECHNICIAN && staff instanceof LabTechnician labTechnician) {
            labTechnician.setCertificationNo(requireText(certificationNo,
                    "Please enter the laboratory certification number."));
            return;
        }

        if (staffType == StaffRole.BLOOD_ADMINISTRATOR && staff instanceof BloodAdministrator administrator) {
            administrator.setDepartment(requireText(department, "Please enter the administrator department."));
            return;
        }

        throw new RuntimeException("Staff role details are inconsistent. Please reload the page and try again.");
    }

    private void replaceStaffSubtype(Long staffId,
                                     StaffRole staffType,
                                     String fullName,
                                     String username,
                                     String phoneNo,
                                     String icNumber,
                                     String gender,
                                     String email,
                                     String storedPassword,
                                     String licenseNo,
                                     String position,
                                     String certificationNo,
                                     String department,
                                     String updatedBy) {
        jdbcTemplate.update("""
                UPDATE staff
                SET staff_type = ?,
                    full_name = ?,
                    username = ?,
                    phone_no = ?,
                    ic_number = ?,
                    gender = ?,
                    email = ?,
                    password = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    last_modified_by = ?
                WHERE staff_id = ?
                """, staffType.name(), fullName, username, phoneNo, icNumber, gender, email, storedPassword,
                updatedBy, staffId);

        jdbcTemplate.update("DELETE FROM medical_staff WHERE staff_id = ?", staffId);
        jdbcTemplate.update("DELETE FROM lab_technician WHERE staff_id = ?", staffId);
        jdbcTemplate.update("DELETE FROM blood_administrator WHERE staff_id = ?", staffId);

        if (staffType == StaffRole.MEDICAL_STAFF) {
            jdbcTemplate.update("""
                    INSERT INTO medical_staff (staff_id, license_no, position)
                    VALUES (?, ?, ?)
                    """, staffId, requireText(licenseNo, "Please enter the medical license number."),
                    requireText(position, "Please enter the medical staff position."));
            return;
        }

        if (staffType == StaffRole.LAB_TECHNICIAN) {
            jdbcTemplate.update("""
                    INSERT INTO lab_technician (staff_id, certification_no)
                    VALUES (?, ?)
                    """, staffId, requireText(certificationNo, "Please enter the laboratory certification number."));
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO blood_administrator (staff_id, department)
                VALUES (?, ?)
                """, staffId, requireText(department, "Please enter the administrator department."));
    }

    private void deleteStaffRecord(Staff staff) {
        deleteProfilePhotoFile(staff.getProfilePhoto());
        staffRepository.delete(staff);
    }

    private List<Long> uniqueStaffIds(List<Long> staffIds) {
        List<Long> uniqueStaffIds = new ArrayList<>();
        if (staffIds == null) {
            return uniqueStaffIds;
        }

        for (Long staffId : staffIds) {
            if (staffId != null && !uniqueStaffIds.contains(staffId)) {
                uniqueStaffIds.add(staffId);
            }
        }

        return uniqueStaffIds;
    }

    private void applyAccountStatus(@NonNull Long staffId, boolean active) {
        jdbcTemplate.update("CALL sp_set_staff_account_status(?, ?)", staffId, active);
    }

    private String storeProfilePhoto(MultipartFile photoFile, String filePrefix) throws Exception {
        if (photoFile == null || photoFile.isEmpty()) {
            return DEFAULT_PHOTO;
        }

        return "staff/" + storeUploadedFile(photoFile, filePrefix);
    }

    private String storeUploadedFile(MultipartFile photoFile, String filePrefix) throws Exception {
        Path uploadDir = Paths.get("uploads", "staff").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String originalFilename = photoFile.getOriginalFilename();
        String extension = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String newFileName = filePrefix + "_" + System.currentTimeMillis() + extension;
        Path destination = uploadDir.resolve(newFileName);
        File destinationFile = Objects.requireNonNull(destination.toFile(), "Destination file must not be null.");
        photoFile.transferTo(destinationFile);
        return newFileName;
    }

    private String resolveProfilePhotoUrl(String photoPath) {
        String normalizedPhotoPath = trimToNull(photoPath);
        if (normalizedPhotoPath == null || isDefaultProfilePhoto(normalizedPhotoPath)) {
            return null;
        }

        String publicPath = normalizedPhotoPath.replace("\\", "/");
        Path uploadRoot = Paths.get("uploads").toAbsolutePath().normalize();
        Path uploadedPhoto = uploadRoot.resolve(publicPath).normalize();

        if (!uploadedPhoto.startsWith(uploadRoot) || !Files.isRegularFile(uploadedPhoto)) {
            return null;
        }

        return "/" + publicPath;
    }

    private boolean isDefaultProfilePhoto(String photoPath) {
        String normalizedPhotoPath = photoPath.replace("\\", "/");
        return DEFAULT_PHOTO.equals(normalizedPhotoPath) || LEGACY_DEFAULT_PHOTO.equals(normalizedPhotoPath);
    }

    private void deleteProfilePhotoFile(String photoPath) {
        String normalizedPhotoPath = trimToNull(photoPath);
        if (normalizedPhotoPath == null || isDefaultProfilePhoto(normalizedPhotoPath)) {
            return;
        }

        File oldFile = Paths.get("uploads").resolve(normalizedPhotoPath).toAbsolutePath().normalize().toFile();
        if (oldFile.exists()) {
            oldFile.delete();
        }
    }

    private @NonNull Long requireStaffId(Staff staff) {
        return Objects.requireNonNull(staff.getStaffId(), "Staff ID must not be null.");
    }

    private Staff findStaffById(@NonNull Long staffId) {
        return staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Staff not found."));
    }

    private Staff findStaffByUsername(@NonNull String username) {
        return staffRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Staff not found."));
    }

    private boolean isCurrentUser(Staff staff, String currentUsername) {
        return currentUsername != null && currentUsername.equalsIgnoreCase(emptyText(staff.getUsername()));
    }

    private String defaultText(String value) {
        if (value == null || value.isBlank()) {
            return "Not provided";
        }

        return value;
    }

    private String emptyText(String value) {
        return value == null ? "" : value.trim();
    }

    private StaffRole requireStaffType(StaffRole staffType) {
        if (staffType == null) {
            throw new RuntimeException("Please select a staff role.");
        }

        return staffType;
    }

    private String requirePassword(String password, boolean required) {
        String normalizedPassword = trimToNull(password);
        if (normalizedPassword == null) {
            if (required) {
                throw new RuntimeException("Please enter a password.");
            }
            return null;
        }

        if (normalizedPassword.length() < 8) {
            throw new RuntimeException("Password must contain at least 8 characters.");
        }

        return normalizedPassword;
    }

    private String prepareStoredPassword(String password) {
        String normalizedPassword = PasswordHashSupport.normalizeStoredPassword(password);
        if (PasswordHashSupport.isBcryptHash(normalizedPassword)) {
            return normalizedPassword;
        }

        return passwordEncoder.encode(normalizedPassword);
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }

        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String formatTextValue(String value) {
        if (value == null || value.isBlank()) {
            return "Not provided";
        }

        String[] words = value.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }

        return builder.toString();
    }

    private String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "SP";
        }

        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();

        for (String part : parts) {
            if (!part.isBlank()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }

            if (initials.length() == 2) {
                break;
            }
        }

        if (initials.length() == 0) {
            return "SP";
        }

        return initials.toString();
    }
}
