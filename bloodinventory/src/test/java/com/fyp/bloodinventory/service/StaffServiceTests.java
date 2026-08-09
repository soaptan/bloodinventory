package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.entity.Staff;
import com.fyp.bloodinventory.repository.BloodAdministratorRepository;
import com.fyp.bloodinventory.repository.LabTechnicianRepository;
import com.fyp.bloodinventory.repository.MedicalStaffRepository;
import com.fyp.bloodinventory.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffServiceTests {

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private MedicalStaffRepository medicalStaffRepository;
    @Mock
    private LabTechnicianRepository labTechnicianRepository;
    @Mock
    private BloodAdministratorRepository bloodAdministratorRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DatabaseAuditContextService auditContextService;

    private StaffService staffService;

    @BeforeEach
    void setUp() {
        staffService = new StaffService(
                staffRepository,
                medicalStaffRepository,
                labTechnicianRepository,
                bloodAdministratorRepository,
                passwordEncoder,
                jdbcTemplate,
                auditContextService
        );
    }

    @Test
    void archiveSelectedStaffDeactivatesUniqueAccountsAndEndsTheirSessions() {
        Staff staff = staff(21L, "archived.user", true);
        when(staffRepository.findById(21L)).thenReturn(Optional.of(staff));

        int archivedCount = staffService.archiveSelectedStaff(List.of(21L, 21L), "admin");

        assertThat(archivedCount).isEqualTo(1);
        verify(jdbcTemplate).update("CALL sp_set_staff_account_status(?, ?)", 21L, false);
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("staff_login_session") && sql.contains("ACCOUNT_ARCHIVED")),
                eq("archived.user")
        );
        verify(staffRepository, never()).delete(staff);
    }

    @Test
    void archiveSelectedStaffRejectsAccountsThatAreAlreadyArchived() {
        Staff staff = staff(22L, "inactive.user", false);
        when(staffRepository.findById(22L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffService.archiveSelectedStaff(List.of(22L), "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Only active staff accounts can be archived.");

        verifyNoInteractions(jdbcTemplate);
        verify(staffRepository, never()).delete(staff);
    }

    @Test
    void archiveSelectedStaffProtectsTheSignedInAdministrator() {
        Staff staff = staff(23L, "admin", true);
        when(staffRepository.findById(23L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffService.archiveSelectedStaff(List.of(23L), "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Deselect your own account before archiving staff accounts.");

        verifyNoInteractions(jdbcTemplate);
        verify(staffRepository, never()).delete(staff);
    }

    @Test
    void restoreSelectedStaffReactivatesUniqueArchivedAccounts() {
        Staff staff = staff(24L, "returning.user", false);
        when(staffRepository.findById(24L)).thenReturn(Optional.of(staff));

        int restoredCount = staffService.restoreSelectedStaff(List.of(24L, 24L));

        assertThat(restoredCount).isEqualTo(1);
        verify(jdbcTemplate).update("CALL sp_set_staff_account_status(?, ?)", 24L, true);
        verify(staffRepository, never()).delete(staff);
    }

    @Test
    void restoreSelectedStaffRejectsAccountsThatAreAlreadyActive() {
        Staff staff = staff(25L, "active.user", true);
        when(staffRepository.findById(25L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffService.restoreSelectedStaff(List.of(25L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Only archived staff accounts can be restored.");

        verifyNoInteractions(jdbcTemplate);
        verify(staffRepository, never()).delete(staff);
    }

    @Test
    void updateOwnPasswordRejectsPasswordWithoutAllRequiredCharacterTypes() {
        Staff staff = staff(26L, "secure.user", true);
        staff.setPassword("stored-password-hash");
        when(staffRepository.findByUsername("secure.user")).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffService.updateOwnPassword(
                "secure.user",
                "Current1!",
                "lowercase1!",
                "lowercase1!"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Password must include uppercase and lowercase letters, a number, and a special character.");

        verify(staffRepository, never()).save(staff);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateOwnPasswordRejectsWhitespaceAndPasswordsLongerThanBcryptLimit() {
        Staff whitespaceStaff = staff(27L, "space.user", true);
        whitespaceStaff.setPassword("stored-password-hash");
        when(staffRepository.findByUsername("space.user")).thenReturn(Optional.of(whitespaceStaff));

        assertThatThrownBy(() -> staffService.updateOwnPassword(
                "space.user",
                "Current1!",
                "New Password1!",
                "New Password1!"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Password cannot contain spaces.");

        String tooLongPassword = "Aa1!" + "x".repeat(69);
        assertThatThrownBy(() -> staffService.updateOwnPassword(
                "space.user",
                "Current1!",
                tooLongPassword,
                tooLongPassword
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Password must be between 8 and 72 characters.");

        verify(staffRepository, never()).save(whitespaceStaff);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateOwnPasswordRejectsReusingTheStoredPassword() {
        Staff staff = staff(28L, "reuse.user", true);
        staff.setPassword("stored-password-hash");
        when(staffRepository.findByUsername("reuse.user")).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches("Current1!", "stored-password-hash")).thenReturn(true);

        assertThatThrownBy(() -> staffService.updateOwnPassword(
                "reuse.user",
                "Current1!",
                "Current1!",
                "Current1!"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("New password must be different from your current password.");

        verify(staffRepository, never()).save(staff);
    }

    @Test
    void updateOwnPasswordEncodesAndSavesAValidNewPassword() {
        Staff staff = staff(29L, "change.user", true);
        staff.setPassword("stored-password-hash");
        when(staffRepository.findByUsername("change.user")).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches("OldPassword1!", "stored-password-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword2!", "stored-password-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword2!")).thenReturn("new-stored-password-hash");

        staffService.updateOwnPassword(
                "change.user",
                "OldPassword1!",
                "NewPassword2!",
                "NewPassword2!"
        );

        assertThat(staff.getPassword()).isEqualTo("new-stored-password-hash");
        verify(staffRepository).save(staff);
    }

    private Staff staff(Long staffId, String username, boolean active) {
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        staff.setUsername(username);
        staff.setActive(active);
        return staff;
    }
}
