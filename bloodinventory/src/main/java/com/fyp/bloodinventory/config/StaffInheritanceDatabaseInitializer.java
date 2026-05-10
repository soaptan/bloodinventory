package com.fyp.bloodinventory.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("legacy-db-init")
public class StaffInheritanceDatabaseInitializer {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    CommandLineRunner initializeStaffInheritanceTables(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS medical_staff (
                        staff_id BIGINT PRIMARY KEY REFERENCES staff(staff_id) ON DELETE CASCADE,
                        license_no VARCHAR(50),
                        position VARCHAR(50)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS lab_technician (
                        staff_id BIGINT PRIMARY KEY REFERENCES staff(staff_id) ON DELETE CASCADE,
                        certification_no VARCHAR(50)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS blood_administrator (
                        staff_id BIGINT PRIMARY KEY REFERENCES staff(staff_id) ON DELETE CASCADE,
                        department VARCHAR(100)
                    )
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE medical_staff
                    ADD COLUMN IF NOT EXISTS license_no VARCHAR(50)
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE medical_staff
                    ADD COLUMN IF NOT EXISTS position VARCHAR(50)
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE lab_technician
                    ADD COLUMN IF NOT EXISTS certification_no VARCHAR(50)
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE blood_administrator
                    ADD COLUMN IF NOT EXISTS department VARCHAR(100)
                    """);

            jdbcTemplate.execute("""
                    INSERT INTO medical_staff (staff_id, license_no, position)
                    SELECT s.staff_id,
                           'MIGRATED-MED-' || s.staff_id,
                           'Medical Staff'
                    FROM staff s
                    WHERE s.staff_type = 'MEDICAL_STAFF'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM medical_staff ms
                          WHERE ms.staff_id = s.staff_id
                      )
                    """);

            jdbcTemplate.execute("""
                    INSERT INTO lab_technician (staff_id, certification_no)
                    SELECT s.staff_id,
                           'MIGRATED-LAB-' || s.staff_id
                    FROM staff s
                    WHERE s.staff_type = 'LAB_TECHNICIAN'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM lab_technician lt
                          WHERE lt.staff_id = s.staff_id
                      )
                    """);

            jdbcTemplate.execute("""
                    INSERT INTO blood_administrator (staff_id, department)
                    SELECT s.staff_id,
                           'System Administration'
                    FROM staff s
                    WHERE s.staff_type = 'BLOOD_ADMINISTRATOR'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM blood_administrator ba
                          WHERE ba.staff_id = s.staff_id
                      )
                    """);

            jdbcTemplate.execute("""
                    DELETE FROM medical_staff ms
                    USING staff s
                    WHERE ms.staff_id = s.staff_id
                      AND s.staff_type <> 'MEDICAL_STAFF'
                    """);

            jdbcTemplate.execute("""
                    DELETE FROM lab_technician lt
                    USING staff s
                    WHERE lt.staff_id = s.staff_id
                      AND s.staff_type <> 'LAB_TECHNICIAN'
                    """);

            jdbcTemplate.execute("""
                    DELETE FROM blood_administrator ba
                    USING staff s
                    WHERE ba.staff_id = s.staff_id
                      AND s.staff_type <> 'BLOOD_ADMINISTRATOR'
                    """);

            jdbcTemplate.execute("""
                    UPDATE medical_staff
                    SET license_no = 'MIGRATED-MED-' || staff_id
                    WHERE license_no IS NULL OR TRIM(license_no) = ''
                    """);

            jdbcTemplate.execute("""
                    UPDATE medical_staff
                    SET position = 'Medical Staff'
                    WHERE position IS NULL OR TRIM(position) = ''
                    """);

            jdbcTemplate.execute("""
                    UPDATE lab_technician
                    SET certification_no = 'MIGRATED-LAB-' || staff_id
                    WHERE certification_no IS NULL OR TRIM(certification_no) = ''
                    """);

            jdbcTemplate.execute("""
                    UPDATE blood_administrator
                    SET department = 'System Administration'
                    WHERE department IS NULL OR TRIM(department) = ''
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE medical_staff
                    ALTER COLUMN license_no SET NOT NULL
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE medical_staff
                    ALTER COLUMN position SET NOT NULL
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE lab_technician
                    ALTER COLUMN certification_no SET NOT NULL
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE blood_administrator
                    ALTER COLUMN department SET NOT NULL
                    """);
        };
    }
}
