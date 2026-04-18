package com.fyp.bloodinventory.service;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

    private final StaffRepository staffRepository;
    private final MedicalStaffRepository medicalStaffRepository;
    private final LabTechnicianRepository labTechnicianRepository;
    private final BloodAdministratorRepository bloodAdministratorRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffService(
            StaffRepository staffRepository,
            MedicalStaffRepository medicalStaffRepository,
            LabTechnicianRepository labTechnicianRepository,
            BloodAdministratorRepository bloodAdministratorRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.staffRepository = staffRepository;
        this.medicalStaffRepository = medicalStaffRepository;
        this.labTechnicianRepository = labTechnicianRepository;
        this.bloodAdministratorRepository = bloodAdministratorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Staff registerStaff(StaffRegistrationRequest request) {
        if (staffRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (staffRepository.existsByIcNumber(request.getIcNumber())) {
            throw new RuntimeException("IC number already exists");
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && staffRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        if (request.getStaffType() == null) {
            throw new RuntimeException("Staff role is required");
        }

        Staff staff = new Staff();
        staff.setFullName(request.getFullName());
        staff.setUsername(request.getUsername());
        staff.setPassword(passwordEncoder.encode(request.getPassword()));
        staff.setPhoneNo(request.getPhoneNo());
        staff.setIcNumber(request.getIcNumber());
        staff.setGender(request.getGender());
        staff.setEmail(request.getEmail());
        staff.setStaffType(request.getStaffType());

        Staff savedStaff = staffRepository.save(staff);

        if (request.getStaffType() == StaffRole.MEDICAL_STAFF) {
            if (request.getLicenseNo() == null || request.getLicenseNo().isBlank()) {
                throw new RuntimeException("License number is required for medical staff");
            }
            if (request.getPosition() == null || request.getPosition().isBlank()) {
                throw new RuntimeException("Position is required for medical staff");
            }

            MedicalStaff medicalStaff = new MedicalStaff();
            medicalStaff.setStaff(savedStaff);
            medicalStaff.setLicenseNo(request.getLicenseNo());
            medicalStaff.setPosition(request.getPosition());
            medicalStaffRepository.save(medicalStaff);

        } else if (request.getStaffType() == StaffRole.LAB_TECHNICIAN) {
            if (request.getCertificationNo() == null || request.getCertificationNo().isBlank()) {
                throw new RuntimeException("Certification number is required for lab technician");
            }

            LabTechnician labTechnician = new LabTechnician();
            labTechnician.setStaff(savedStaff);
            labTechnician.setCertificationNo(request.getCertificationNo());
            labTechnicianRepository.save(labTechnician);

        } else if (request.getStaffType() == StaffRole.BLOOD_ADMINISTRATOR) {
            if (request.getDepartment() == null || request.getDepartment().isBlank()) {
                throw new RuntimeException("Department is required for blood administrator");
            }

            BloodAdministrator admin = new BloodAdministrator();
            admin.setStaff(savedStaff);
            admin.setDepartment(request.getDepartment());
            bloodAdministratorRepository.save(admin);
        }

        return savedStaff;
    }
}