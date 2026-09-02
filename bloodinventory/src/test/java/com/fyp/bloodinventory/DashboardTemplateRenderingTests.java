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
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Objects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "nvidia.nim.api-key=")
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
            "/admin/storage/create",
            "/admin/inventory",
            "/admin/reports",
            "/admin/audit",
            "/admin/deferral-rules",
            "/admin/deferral-rules/create",
            "/admin/settings",
            "/admin/staff/management",
            "/medical/dashboard",
            "/medical/donor-eligibility",
            "/medical/donor-eligibility/assessment",
            "/medical/donations",
            "/medical/donations/record",
            "/medical/transfusion",
            "/medical/transfusion/record",
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
                .andExpect(expect(content().string(containsString("system-preferences.css"))))
                .andExpect(expect(content().string(containsString("class=\"skip-link\" href=\"#main-content\""))))
                .andExpect(expect(content().string(containsString("<main id=\"main-content\" class=\"main\" tabindex=\"-1\">"))))
                .andExpect(expect(content().string(containsString("class=\"app-title\">Blood Inventory Management System"))))
                .andExpect(expect(content().string(containsString("data-sidebar-toggle"))))
                .andExpect(expect(content().string(containsString("aria-controls=\"dashboard-sidebar\""))))
                .andExpect(expect(content().string(containsString("class=\"hamburger-icon\""))))
                .andExpect(expect(content().string(containsString("id=\"dashboard-sidebar\""))))
                .andExpect(expect(content().string(not(containsString("class=\"app-kicker\"")))))
                .andExpect(expect(content().string(not(containsString("class=\"app-subtitle\"")))))
                .andExpect(expect(content().string(containsString("class=\"system-footer\""))))
                .andExpect(expect(content().string(containsString("Copyright &copy; 2026 UTeM | Universiti Teknikal Malaysia Melaka. All Rights Reserved."))))
                .andExpect(expect(content().string(containsString("sweetalert2@11"))));
    }

    @Test
    void administratorDashboardProvidesBalancedOverviewAndRoleSafeQuickActions() throws Exception {
        mockMvc.perform(get("/admin/dashboard").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("stat-grid stat-grid-balanced"))))
                .andExpect(expect(content().string(containsString("aria-label=\"Administrator quick actions\""))))
                .andExpect(expect(content().string(containsString("aria-current=\"page\""))))
                .andExpect(expect(content().string(containsString("href=\"/admin/staff/management\""))))
                .andExpect(expect(content().string(containsString("href=\"/admin/inventory\""))))
                .andExpect(expect(content().string(containsString("href=\"/admin/reports\""))))
                .andExpect(expect(content().string(containsString("href=\"/admin/settings\""))))
                .andExpect(expect(content().string(not(containsString("href=\"/medical/")))));
    }

    @Test
    void interfaceSettingsUsesFixedSystemLogoWithoutUpload() throws Exception {
        mockMvc.perform(get("/admin/settings").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-settings-scroll-content"))))
                .andExpect(expect(content().string(containsString("data-interface-settings-form"))))
                .andExpect(expect(content().string(containsString("name=\"accentColor\""))))
                .andExpect(expect(content().string(containsString("src=\"/images/blood-drop-logo.png\""))))
                .andExpect(expect(content().string(not(containsString("enctype=\"multipart/form-data\"")))))
                .andExpect(expect(content().string(not(containsString("name=\"systemLogo\"")))))
                .andExpect(expect(content().string(not(containsString("data-logo-preview")))));
    }

    @Test
    void systemPreferenceCssEndpointReturnsPersistedThemeVariables() throws Exception {
        mockMvc.perform(get("/css/system-preferences.css"))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css"))))
                .andExpect(expect(content().string(containsString("--system-accent-color"))))
                .andExpect(expect(content().string(containsString("--color-primary"))));
    }

    @Test
    void loginPageLoadsSavedBrandingStylesheet() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("system-preferences.css"))));
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
    void adminReportsPageIncludesIndependentTableFiltersAndSorting() throws Exception {
        mockMvc.perform(get("/admin/reports").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-report-table=\"available-stock\""))))
                .andExpect(expect(content().string(containsString("data-report-table=\"staff-totals\""))))
                .andExpect(expect(content().string(containsString("data-report-table=\"near-expiry\""))))
                .andExpect(expect(content().string(containsString("data-report-table=\"system-activity\""))))
                .andExpect(expect(content().string(containsString("data-report-filter=\"component-type\""))))
                .andExpect(expect(content().string(containsString("data-report-filter=\"status\""))))
                .andExpect(expect(content().string(containsString("data-report-filter=\"staff-type\""))))
                .andExpect(expect(content().string(containsString("data-report-filter=\"module\""))))
                .andExpect(expect(content().string(containsString("data-report-filter=\"action\""))))
                .andExpect(expect(content().string(containsString("data-report-sort"))))
                .andExpect(expect(content().string(containsString("data-report-apply"))))
                .andExpect(expect(content().string(containsString("data-report-reset"))))
                .andExpect(expect(content().string(containsString("data-report-filter-empty"))))
                .andExpect(expect(content().string(containsString("/js/admin-report-table-filters.js"))));
    }

    @Test
    void adminStoragePageLinksToCreateModal() throws Exception {
        mockMvc.perform(get("/admin/storage").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-create-storage-link"))))
                .andExpect(expect(content().string(containsString("href=\"/admin/storage/create\""))))
                .andExpect(expect(content().string(not(containsString("data-storage-create-page")))))
                .andExpect(expect(content().string(not(containsString("data-storage-create-form")))));
    }

    @Test
    void storageCreateRouteOpensModalOverManagementPage() throws Exception {
        mockMvc.perform(get("/admin/storage/create").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-storage-create-page"))))
                .andExpect(expect(content().string(containsString("data-storage-create-form"))))
                .andExpect(expect(content().string(containsString("data-create-storage-location"))))
                .andExpect(expect(content().string(containsString("data-back-to-storage-locations"))))
                .andExpect(expect(content().string(containsString("data-storage-create-modal"))))
                .andExpect(expect(content().string(containsString("admin-create-backdrop"))))
                .andExpect(expect(content().string(containsString("is-open"))))
                .andExpect(expect(content().string(containsString("staff-register-modal admin-create-modal"))))
                .andExpect(expect(content().string(containsString("action=\"/admin/storage/add\""))));
    }

    @Test
    void adminDeferralRulesPageLinksToCreateModal() throws Exception {
        mockMvc.perform(get("/admin/deferral-rules").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-create-deferral-rule-link"))))
                .andExpect(expect(content().string(containsString("href=\"/admin/deferral-rules/create\""))))
                .andExpect(expect(content().string(not(containsString("data-deferral-rule-create-page")))))
                .andExpect(expect(content().string(not(containsString("data-deferral-rule-create-form")))));
    }

    @Test
    void deferralRuleCreateRouteOpensModalOverManagementPage() throws Exception {
        mockMvc.perform(get("/admin/deferral-rules/create").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-deferral-rule-create-page"))))
                .andExpect(expect(content().string(containsString("data-deferral-rule-create-form"))))
                .andExpect(expect(content().string(containsString("data-create-deferral-rule"))))
                .andExpect(expect(content().string(containsString("data-back-to-deferral-rules"))))
                .andExpect(expect(content().string(containsString("data-deferral-rule-create-modal"))))
                .andExpect(expect(content().string(containsString("admin-create-backdrop"))))
                .andExpect(expect(content().string(containsString("is-open"))))
                .andExpect(expect(content().string(containsString("staff-register-modal admin-create-modal"))))
                .andExpect(expect(content().string(containsString("action=\"/admin/deferral-rules/add\""))));
    }

    @Test
    void dashboardAssistantCombinesChatAndAgentInComposer() throws Exception {
        mockMvc.perform(get("/admin/dashboard").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("Pulse AI"))))
                .andExpect(expect(content().string(containsString("data-chatbot-draggable"))))
                .andExpect(expect(content().string(containsString("Assistant - drag to move"))))
                .andExpect(expect(content().string(containsString("data-chatbot-mode-toggle"))))
                .andExpect(expect(content().string(containsString("data-chatbot-mode-menu"))))
                .andExpect(expect(content().string(containsString("data-chatbot-mode-option=\"chat\""))))
                .andExpect(expect(content().string(containsString("data-chatbot-mode-option=\"agent\""))))
                .andExpect(expect(content().string(not(containsString("data-chatbot-agent-tab")))))
                .andExpect(expect(content().string(not(containsString("data-chatbot-agent-banner")))));
    }

    @Test
    void staffManagementArchivesAccountsInsteadOfDeletingRecords() throws Exception {
        mockMvc.perform(get("/admin/staff/management").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-staff-management-summary"))))
                .andExpect(expect(content().string(containsString("data-staff-records-overview"))))
                .andExpect(expect(content().string(not(containsString("<section id=\"add\" class=\"card card-lg gap-md\">")))))
                .andExpect(expect(content().string(containsString("action=\"/admin/staff/archive-selected\""))))
                .andExpect(expect(content().string(containsString("data-selected-staff-archive-form"))))
                .andExpect(expect(content().string(containsString("data-selected-staff-archive-button"))))
                .andExpect(expect(content().string(containsString("action=\"/admin/staff/restore-selected\""))))
                .andExpect(expect(content().string(containsString("data-selected-staff-restore-form"))))
                .andExpect(expect(content().string(containsString("data-selected-staff-restore-button"))))
                .andExpect(expect(content().string(containsString("Only archived staff accounts can be restored."))))
                .andExpect(expect(content().string(containsString("unlock the account, and allow sign-in again"))))
                .andExpect(expect(content().string(containsString("filter to Archived, select the account, and click Restore"))))
                .andExpect(expect(content().string(containsString("Archived Accounts"))))
                .andExpect(expect(content().string(containsString("end active sessions"))))
                .andExpect(expect(content().string(not(containsString("action=\"/admin/staff/delete-selected\"")))))
                .andExpect(expect(content().string(not(containsString("permanently. Continue?")))));
    }

    @Test
    void agentModeReturnsBuiltInGuidanceWhenExternalAiIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/chatbot/ask")
                        .principal(ADMIN_AUTHENTICATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Review inventory risks and tell me what to check first",
                                  "mode": "agent",
                                  "pagePath": "/admin/storage"
                                }
                                """))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(jsonPath("$.success").value(true)))
                .andExpect(expect(jsonPath("$.reply").value(containsString("Goal: Review inventory risks"))))
                .andExpect(expect(jsonPath("$.reply").value(containsString("/admin/inventory"))));
    }

    @Test
    void adminAuditDownloadReturnsAttachment() throws Exception {
        mockMvc.perform(get("/admin/audit/download")
                        .param("format", "csv")
                        .principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("audit-trail.csv"))))
                .andExpect(expect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv"))))
                .andExpect(expect(content().string(containsString("Time UTC,Actor,Role,Activity,Change,Action,Target"))))
                .andExpect(expect(content().string(not(containsString(",Component,Donation,Location,Device,")))));
    }

    @Test
    void adminAuditPageIncludesDownloadAndPrintActions() throws Exception {
        mockMvc.perform(get("/admin/audit").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("action=\"/admin/audit/download\""))))
                .andExpect(expect(content().string(containsString("data-report-print"))))
                .andExpect(expect(content().string(containsString("<th>Activity</th>"))))
                .andExpect(expect(content().string(containsString("<th>Change</th>"))))
                .andExpect(expect(content().string(containsString("<th>Target</th>"))))
                .andExpect(expect(content().string(containsString("<th>Request</th>"))))
                .andExpect(expect(content().string(containsString("Role: "))))
                .andExpect(expect(content().string(containsString("Latest insert, update, and delete records"))))
                .andExpect(expect(content().string(containsString("value=\"INSERT\""))))
                .andExpect(expect(content().string(containsString("value=\"UPDATE\""))))
                .andExpect(expect(content().string(containsString("value=\"DELETE\""))))
                .andExpect(expect(content().string(not(containsString("value=\"VIEW\"")))))
                .andExpect(expect(content().string(not(containsString("value=\"ACTION\"")))))
                .andExpect(expect(content().string(not(containsString("<th>Component</th>")))))
                .andExpect(expect(content().string(not(containsString("<th>Donation</th>")))))
                .andExpect(expect(content().string(not(containsString("<th>Location</th>")))))
                .andExpect(expect(content().string(not(containsString("component, donation, location")))))
                .andExpect(expect(content().string(containsString("Database Audit Trail"))));
    }

    @Test
    void adminAuditPageAlwaysDisplaysLatestTwentyRows() throws Exception {
        mockMvc.perform(get("/admin/audit")
                        .param("sortBy", "oldest")
                        .param("limit", "50")
                        .principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("name=\"sortBy\" value=\"newest\""))))
                .andExpect(expect(content().string(containsString("name=\"limit\" value=\"20\""))))
                .andExpect(expect(content().string(containsString("Showing latest"))))
                .andExpect(expect(content().string(not(containsString("50 rows")))));
    }

    @Test
    void medicalComponentsPageCombinesCompatibilityAndTableFilters() throws Exception {
        mockMvc.perform(get("/medical/components").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-safe-match-filter-form"))))
                .andExpect(expect(content().string(not(containsString("name=\"recipientBloodGroup\"")))))
                .andExpect(expect(content().string(not(containsString(">Recipient Blood Group<")))))
                .andExpect(expect(content().string(containsString("name=\"componentType\""))))
                .andExpect(expect(content().string(containsString("name=\"search\""))))
                .andExpect(expect(content().string(containsString("name=\"donorGroup\""))))
                .andExpect(expect(content().string(containsString("name=\"status\""))))
                .andExpect(expect(content().string(containsString("name=\"match\""))))
                .andExpect(expect(content().string(containsString("name=\"expiry\""))))
                .andExpect(expect(content().string(containsString("name=\"location\""))))
                .andExpect(expect(content().string(containsString("name=\"sort\""))))
                .andExpect(expect(content().string(containsString("href=\"/medical/components\" data-clear-safe-match-filters"))))
                .andExpect(expect(content().string(not(containsString(">Apply Match<")))))
                .andExpect(expect(content().string(not(containsString(">Match Criteria<")))));
    }

    @Test
    void medicalDonationsPageLinksToCollectionModalAndOmitsComponentStatus() throws Exception {
        mockMvc.perform(get("/medical/donations").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-record-collection-link"))))
                .andExpect(expect(content().string(containsString("href=\"/medical/donations/record\""))))
                .andExpect(expect(content().string(containsString("/js/admin-create-modals.js"))))
                .andExpect(expect(content().string(not(containsString("data-donation-form-page")))))
                .andExpect(expect(content().string(not(containsString("data-donation-create-modal")))))
                .andExpect(expect(content().string(not(containsString("<th>Status</th>")))))
                .andExpect(expect(content().string(not(containsString("donation.componentStatuses")))));
    }

    @Test
    void donationRecordRouteOpensCollectionModalOverLog() throws Exception {
        mockMvc.perform(get("/medical/donations/record").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-donation-form-page"))))
                .andExpect(expect(content().string(containsString("data-create-donation"))))
                .andExpect(expect(content().string(containsString("data-donor-select-search"))))
                .andExpect(expect(content().string(containsString("data-donor-select"))))
                .andExpect(expect(content().string(containsString("data-back-to-collection-log"))))
                .andExpect(expect(content().string(containsString("data-donation-create-modal"))))
                .andExpect(expect(content().string(containsString("admin-create-backdrop"))))
                .andExpect(expect(content().string(containsString("staff-register-modal admin-create-modal"))))
                .andExpect(expect(content().string(containsString("is-open"))))
                .andExpect(expect(content().string(containsString("action=\"/medical/donations\""))));
    }

    @Test
    void transfusionPageLinksToRecordModal() throws Exception {
        mockMvc.perform(get("/medical/transfusion").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-record-transfusion-link"))))
                .andExpect(expect(content().string(containsString("href=\"/medical/transfusion/record\""))))
                .andExpect(expect(content().string(containsString("/js/admin-create-modals.js"))))
                .andExpect(expect(content().string(not(containsString("data-transfusion-form-page")))))
                .andExpect(expect(content().string(not(containsString("data-transfusion-create-modal")))));
    }

    @Test
    void transfusionRecordRouteOpensEventModalOverRecords() throws Exception {
        mockMvc.perform(get("/medical/transfusion/record").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-transfusion-form-page"))))
                .andExpect(expect(content().string(containsString("data-create-transfusion"))))
                .andExpect(expect(content().string(containsString("data-back-to-transfusion-records"))))
                .andExpect(expect(content().string(containsString("data-patient-mode-options"))))
                .andExpect(expect(content().string(containsString("data-patient-panel=\"existing\""))))
                .andExpect(expect(content().string(containsString("data-patient-panel=\"new\""))))
                .andExpect(expect(content().string(containsString("Register New Patient"))))
                .andExpect(expect(content().string(not(containsString("<option value=\"\">New patient</option>")))))
                .andExpect(expect(content().string(containsString("name=\"componentId\""))))
                .andExpect(expect(content().string(containsString("data-transfusion-create-modal"))))
                .andExpect(expect(content().string(containsString("admin-create-backdrop"))))
                .andExpect(expect(content().string(containsString("staff-register-modal admin-create-modal"))))
                .andExpect(expect(content().string(containsString("is-open"))))
                .andExpect(expect(content().string(containsString("action=\"/medical/transfusion\""))));
    }

    @Test
    void labDashboardUsesOneSynchronizedCurrentMonthTrend() throws Exception {
        mockMvc.perform(get("/lab/dashboard").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("This Month (Lab) ·"))))
                .andExpect(expect(content().string(containsString("data-lab-trend-source"))))
                .andExpect(expect(content().string(containsString("data-pending="))))
                .andExpect(expect(content().string(containsString("data-completed="))))
                .andExpect(expect(content().string(containsString("const labelStep = Math.max(1, Math.ceil(points.length / 8))"))))
                .andExpect(expect(content().string(not(containsString(".slice(-8)")))))
                .andExpect(expect(content().string(not(containsString("pendingTrendTests")))))
                .andExpect(expect(content().string(not(containsString("recentLabTrendTests")))));
    }

    @Test
    void donorEligibilityPageLinksToAssessmentModal() throws Exception {
        mockMvc.perform(get("/medical/donor-eligibility").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-assessment-controls-link"))))
                .andExpect(expect(content().string(containsString("href=\"/medical/donor-eligibility/assessment\""))))
                .andExpect(expect(content().string(containsString("/medical/donor-eligibility/assessment?donorId="))))
                .andExpect(expect(content().string(containsString("/js/admin-create-modals.js"))))
                .andExpect(expect(content().string(not(containsString("data-assessment-form-page")))))
                .andExpect(expect(content().string(not(containsString("data-assessment-create-modal")))));
    }

    @Test
    void donorAssessmentRouteOpensAssessmentModalOverDonorRecords() throws Exception {
        mockMvc.perform(get("/medical/donor-eligibility/assessment").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-assessment-form-page"))))
                .andExpect(expect(content().string(containsString("data-assessment-controls-grid"))))
                .andExpect(expect(content().string(containsString("data-apply-assessment"))))
                .andExpect(expect(content().string(containsString("Apply Assessment"))))
                .andExpect(expect(content().string(containsString("data-back-to-donor-records"))))
                .andExpect(expect(content().string(containsString("data-deferral-donor-search"))))
                .andExpect(expect(content().string(containsString("data-deferral-donor-select"))))
                .andExpect(expect(content().string(containsString("Search donor name, IC number, or blood group"))))
                .andExpect(expect(content().string(containsString("data-assessment-create-modal"))))
                .andExpect(expect(content().string(containsString("admin-create-backdrop"))))
                .andExpect(expect(content().string(containsString("staff-register-modal admin-create-modal"))))
                .andExpect(expect(content().string(containsString("is-open"))))
                .andExpect(expect(content().string(containsString("action=\"/medical/donor-eligibility/donors\""))));
    }

    @Test
    void deferralRulesLoadCreateModalBehavior() throws Exception {
        mockMvc.perform(get("/admin/deferral-rules/create").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("/js/admin-create-modals.js"))))
                .andExpect(expect(content().string(containsString("data-lock-type-select"))))
                .andExpect(expect(content().string(containsString("data-cooling-days-input"))))
                .andExpect(expect(content().string(not(containsString("coolingInput.value = \"0\"")))));
    }

    @Test
    void profileSecurityPanelUsesCurrentPasswordOnly() throws Exception {
        mockMvc.perform(get("/profile").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("action=\"/profile/password\""))))
                .andExpect(expect(content().string(containsString("data-password-form"))))
                .andExpect(expect(content().string(containsString("Current Password"))))
                .andExpect(expect(content().string(containsString("data-password-toggle=\"current-password\""))))
                .andExpect(expect(content().string(containsString("data-password-toggle=\"new-password\""))))
                .andExpect(expect(content().string(containsString("data-password-toggle=\"confirm-password\""))))
                .andExpect(expect(content().string(containsString("New password requirements"))))
                .andExpect(expect(content().string(containsString("Password strength"))))
                .andExpect(expect(content().string(containsString("maxlength=\"72\""))))
                .andExpect(expect(content().string(containsString("Update Password"))))
                .andExpect(expect(content().string(not(containsString("Send Verify Code")))))
                .andExpect(expect(content().string(not(containsString("Reset With Code")))))
                .andExpect(expect(content().string(not(containsString("action=\"/profile/password-reset-email\"")))))
                .andExpect(expect(content().string(not(containsString("action=\"/profile/password-reset-code\"")))))
                .andExpect(expect(content().string(not(containsString("name=\"verificationCode\"")))));
    }

    @Test
    void profilePhotoUploadUsesAvatarMenu() throws Exception {
        mockMvc.perform(get("/profile").principal(ADMIN_AUTHENTICATION))
                .andExpect(expect(status().isOk()))
                .andExpect(expect(content().string(containsString("data-profile-photo-form"))))
                .andExpect(expect(content().string(containsString("data-photo-menu-toggle"))))
                .andExpect(expect(content().string(containsString("Upload photo"))))
                .andExpect(expect(content().string(not(containsString("Update Photo")))));
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
