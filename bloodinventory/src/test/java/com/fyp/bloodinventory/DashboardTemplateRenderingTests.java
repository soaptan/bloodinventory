package com.fyp.bloodinventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;
import java.util.Objects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class DashboardTemplateRenderingTests {

    private static final Authentication ADMIN_AUTHENTICATION = new UsernamePasswordAuthenticationToken(
            "admin",
            "N/A",
            List.of(new SimpleGrantedAuthority("ROLE_BLOOD_ADMINISTRATOR"))
    );

    private final MockMvc mockMvc;

    @Autowired
    DashboardTemplateRenderingTests(MockMvc mockMvc) {
        this.mockMvc = Objects.requireNonNull(mockMvc, "MockMvc must not be null.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/dashboard",
            "/admin/storage",
            "/admin/inventory",
            "/admin/reports",
            "/admin/deferral-rules",
            "/admin/settings",
            "/admin/staff/management",
            "/medical/dashboard",
            "/medical/donor-eligibility",
            "/medical/donations",
            "/medical/transfusion",
            "/medical/components",
            "/lab/dashboard",
            "/lab/pending-tests",
            "/lab/tti-screening",
            "/lab/component-status",
            "/lab/traceability",
            "/profile"
    })
    void dashboardTemplatesRenderWithMasterLayout(String path) throws Exception {
        mockMvc.perform(get(requiredPath(path)).principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("dashboard-scene"))))
                .andExpect(expect(content().string(containsString("components.css"))))
                .andExpect(expect(content().string(containsString("sweetalert2@11"))));
    }

    @Test
    void adminReportDownloadReturnsAttachment() throws Exception {
        mockMvc.perform(get("/admin/reports/download")
                        .param("type", "summary")
                        .param("format", "csv")
                        .principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("summary-report.csv"))))
                .andExpect(expect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv"))))
                .andExpect(expect(content().string(containsString("Metric,Value"))));
    }

    @Test
    void adminReportsPageIncludesDownloadDialogForm() throws Exception {
        mockMvc.perform(get("/admin/reports").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-report-download-modal"))))
                .andExpect(expect(content().string(containsString("action=\"/admin/reports/download\""))))
                .andExpect(expect(content().string(containsString("name=\"format\""))));
    }

    @Test
    void medicalDonationsPageDoesNotShowComponentStatusColumn() throws Exception {
        mockMvc.perform(get("/medical/donations").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(not(containsString("<th>Status</th>")))))
                .andExpect(expect(content().string(not(containsString("donation.componentStatuses")))));
    }

    @NonNull
    private static String requiredPath(String path) {
        return Objects.requireNonNull(path, "Path must not be null.");
    }

    @NonNull
    private static ResultMatcher expect(ResultMatcher matcher) {
        return Objects.requireNonNull(matcher, "Result matcher must not be null.");
    }
}
