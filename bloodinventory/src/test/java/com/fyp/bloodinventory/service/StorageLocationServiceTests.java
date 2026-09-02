package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.StorageLocationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"all", "null"})
class StorageLocationServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseAuditContextService auditContextService = mock(DatabaseAuditContextService.class);
    private final StorageLocationService service = new StorageLocationService(jdbcTemplate, auditContextService);

    @Test
    void addLocationRejectsBlankDescriptionBeforeDatabaseAccess() {
        StorageLocationRequest request = request("   ", 41L);

        assertThatThrownBy(() -> service.addLocation(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Please enter a storage description.");

        verifyNoInteractions(jdbcTemplate, auditContextService);
    }

    @Test
    void addLocationTrimsUniqueDescriptionBeforeSaving() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("New Cabinet")))
                .thenReturn(false);

        service.addLocation(request("  New Cabinet  ", 41L));

        verify(jdbcTemplate).update(
                eq("CALL sp_add_storage_location(?, ?)"),
                eq("New Cabinet"),
                eq(41L)
        );
        verify(auditContextService).applyCurrentContext();
    }

    @Test
    void addLocationRejectsExistingDescriptionIgnoringCaseAndWhitespace() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("existing cabinet")))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addLocation(request("  existing cabinet  ", 41L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Storage location name already exists.");

        verify(auditContextService, never()).applyCurrentContext();
        verify(jdbcTemplate, never()).update(
                eq("CALL sp_add_storage_location(?, ?)"),
                eq("Existing Cabinet"),
                eq(41L)
        );
    }

    @Test
    void addLocationConvertsDatabaseDuplicateIntoTheSameValidationMessage() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("New Cabinet")))
                .thenReturn(false);
        doThrow(new DataIntegrityViolationException("ux_storage_location_description_normalized"))
                .when(jdbcTemplate)
                .update(eq("CALL sp_add_storage_location(?, ?)"), eq("New Cabinet"), eq(41L));

        assertThatThrownBy(() -> service.addLocation(request("New Cabinet", 41L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Storage location name already exists.");
    }

    private StorageLocationRequest request(String description, Long staffId) {
        StorageLocationRequest request = new StorageLocationRequest();
        request.setDescription(description);
        request.setStaffId(staffId);
        return request;
    }
}
