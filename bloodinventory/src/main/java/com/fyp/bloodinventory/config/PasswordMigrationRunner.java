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
                String normalizedPassword = PasswordHashSupport.normalizeStoredPassword(currentPassword);

                if (normalizedPassword != null && PasswordHashSupport.isBcryptHash(normalizedPassword)) {
                    if (!normalizedPassword.equals(currentPassword)) {
                        staff.setPassword(normalizedPassword);
                        staffRepository.save(staff);
                        System.out.println("Password hash normalized for user: " + staff.getUsername());
                    }
                } else if (normalizedPassword != null && !normalizedPassword.isBlank()) {
                    staff.setPassword(passwordEncoder.encode(normalizedPassword));
                    staffRepository.save(staff);
                    System.out.println("Password updated for user: " + staff.getUsername());
                }
            }

            System.out.println("Password migration completed.");
        };
    }
}
