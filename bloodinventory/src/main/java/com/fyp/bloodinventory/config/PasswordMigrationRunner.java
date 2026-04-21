package com.fyp.bloodinventory.config;

import com.fyp.bloodinventory.entity.Staff;
import com.fyp.bloodinventory.repository.StaffRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class PasswordMigrationRunner {

    @Bean
    CommandLineRunner migratePasswords(
            StaffRepository staffRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            List<Staff> staffList = staffRepository.findAll();

            for (Staff staff : staffList) {
                String currentPassword = staff.getPassword();

                if (currentPassword != null && !currentPassword.startsWith("$2a$")) {
                    staff.setPassword(passwordEncoder.encode(currentPassword));
                    staffRepository.save(staff);
                    System.out.println("Password updated for user: " + staff.getUsername());
                }
            }

            System.out.println("Password migration completed.");
        };
    }
}