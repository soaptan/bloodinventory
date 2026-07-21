package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.SystemUiSettingsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"all", "null"})
class SystemSettingsServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SystemSettingsService service = new SystemSettingsService(jdbcTemplate);

    @Test
    void interfaceUpdatePersistsFontScaleAndAccent() {
        SystemUiSettingsRequest request = new SystemUiSettingsRequest();
        request.setFontScale(1.1);
        request.setAccentColor("#008080");

        service.updateUiSettings(request);

        verify(jdbcTemplate).update(contains("system_setting"), eq("ui_font_scale"), eq("1.10"));
        verify(jdbcTemplate).update(contains("system_setting"), eq("ui_accent_color"), eq("#008080"));
    }

    @Test
    void preferenceStylesheetUsesPersistedAccentAcrossPrimaryControls() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("ui_font_scale")))
                .thenReturn(List.of("1.10"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("ui_accent_color")))
                .thenReturn(List.of("#008080"));

        String css = service.getPreferenceCss();

        assertThat(css)
                .contains("--system-font-scale: 1.10")
                .contains("--system-accent-color: #008080")
                .contains("--color-primary: #008080")
                .contains("body .app-title")
                .contains("font-size: clamp(16px, calc(18px * var(--system-font-scale)), 23px)")
                .contains("body .profile-trigger-label")
                .doesNotContain("body .app-title,\n                body .sidebar-title,\n                body .nav-title")
                .contains("body .btn-primary")
                .contains("body .nav-menu a.active")
                .contains("body input:focus");
    }
}
