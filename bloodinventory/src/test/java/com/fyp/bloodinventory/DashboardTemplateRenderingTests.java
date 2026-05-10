package com.fyp.bloodinventory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class DashboardTemplateRenderingTests {

    private static final Authentication ADMIN_AUTHENTICATION = new UsernamePasswordAuthenticationToken(
            "admin",
            "N/A",
            List.of(new SimpleGrantedAuthority("ROLE_BLOOD_ADMINISTRATOR"))
    );

    @Autowired
    private MockMvc mockMvc;

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
        mockMvc.perform(get(path).principal(ADMIN_AUTHENTICATION))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dashboard-scene")))
                .andExpect(content().string(containsString("sweetalert2@11")));
    }
}
