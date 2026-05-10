package com.fyp.bloodinventory.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("legacy-db-init")
public class StaffModuleAccessDatabaseInitializer {

    @Bean
    CommandLineRunner initializeStaffModuleAccessDatabaseObjects(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS staff_module_access (
                        access_id BIGSERIAL PRIMARY KEY,
                        staff_type VARCHAR(40) NOT NULL,
                        module_key VARCHAR(80) NOT NULL,
                        module_name VARCHAR(120) NOT NULL,
                        url_pattern VARCHAR(180) NOT NULL,
                        is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        sort_order INTEGER NOT NULL DEFAULT 100,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT uk_staff_module_access_role_key UNIQUE (staff_type, module_key)
                    )
                    """);

            jdbcTemplate.execute("ALTER TABLE staff_module_access ADD COLUMN IF NOT EXISTS module_name VARCHAR(120)");
            jdbcTemplate.execute("ALTER TABLE staff_module_access ADD COLUMN IF NOT EXISTS url_pattern VARCHAR(180)");
            jdbcTemplate.execute("ALTER TABLE staff_module_access ADD COLUMN IF NOT EXISTS is_enabled BOOLEAN NOT NULL DEFAULT TRUE");
            jdbcTemplate.execute("ALTER TABLE staff_module_access ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 100");
            jdbcTemplate.execute("ALTER TABLE staff_module_access ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE staff_module_access ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE staff_module_access ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE staff_module_access ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP");
            jdbcTemplate.execute("UPDATE staff_module_access SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL");
            jdbcTemplate.execute("UPDATE staff_module_access SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL");

            seedRule(jdbcTemplate, "BLOOD_ADMINISTRATOR", "admin_dashboard", "Administrator Dashboard", "/admin/dashboard", 10);
            seedRule(jdbcTemplate, "BLOOD_ADMINISTRATOR", "staff_management", "Staff Management", "/admin/staff/**", 20);
            seedRule(jdbcTemplate, "BLOOD_ADMINISTRATOR", "storage_config", "Storage Configuration", "/admin/storage/**", 30);
            seedRule(jdbcTemplate, "BLOOD_ADMINISTRATOR", "deferral_rules", "Deferral Rules", "/admin/deferral-rules/**", 40);
            seedRule(jdbcTemplate, "BLOOD_ADMINISTRATOR", "inventory_monitoring", "Inventory Monitoring", "/admin/inventory/**", 50);
            seedRule(jdbcTemplate, "BLOOD_ADMINISTRATOR", "reports_alerts", "Reports and Alerts", "/admin/reports/**", 60);
            seedRule(jdbcTemplate, "BLOOD_ADMINISTRATOR", "system_settings", "System Settings", "/admin/settings/**", 70);

            seedRule(jdbcTemplate, "MEDICAL_STAFF", "medical_dashboard", "Medical Dashboard", "/medical/dashboard", 10);
            seedRule(jdbcTemplate, "MEDICAL_STAFF", "donor_eligibility", "Donor Eligibility", "/medical/donor-eligibility/**", 20);
            seedRule(jdbcTemplate, "MEDICAL_STAFF", "blood_collection", "Blood Collection", "/medical/donations/**", 30);
            seedRule(jdbcTemplate, "MEDICAL_STAFF", "transfusion_request", "Transfusion Request", "/medical/transfusion/**", 40);
            seedRule(jdbcTemplate, "MEDICAL_STAFF", "safe_blood_match", "Safe Blood Match", "/medical/components/**", 50);

            seedRule(jdbcTemplate, "LAB_TECHNICIAN", "lab_dashboard", "Lab Dashboard", "/lab/dashboard", 10);
            seedRule(jdbcTemplate, "LAB_TECHNICIAN", "pending_tests", "Pending Test Queue", "/lab/pending-tests/**", 20);
            seedRule(jdbcTemplate, "LAB_TECHNICIAN", "tti_screening", "TTI Screening", "/lab/tti-screening/**", 30);
            seedRule(jdbcTemplate, "LAB_TECHNICIAN", "component_status", "Component Status", "/lab/component-status/**", 40);
            seedRule(jdbcTemplate, "LAB_TECHNICIAN", "traceability", "Traceability", "/lab/traceability/**", 50);
        };
    }

    private void seedRule(JdbcTemplate jdbcTemplate,
                          String staffType,
                          String moduleKey,
                          String moduleName,
                          String urlPattern,
                          int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO staff_module_access (
                    staff_type,
                    module_key,
                    module_name,
                    url_pattern,
                    is_enabled,
                    sort_order,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (staff_type, module_key) DO NOTHING
                """, staffType, moduleKey, moduleName, urlPattern, sortOrder);
    }
}
