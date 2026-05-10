package com.fyp.bloodinventory.config;

import com.fyp.bloodinventory.entity.BloodAdministrator;
import com.fyp.bloodinventory.repository.BloodAdministratorRepository;
import com.fyp.bloodinventory.repository.StaffRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("legacy-db-init")
public class DataInitializer {

    @Bean
    CommandLineRunner initAdminUser(
            StaffRepository staffRepository,
            BloodAdministratorRepository bloodAdministratorRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (staffRepository.findByUsername("admin").isEmpty()) {
                BloodAdministrator admin = new BloodAdministrator();
                admin.setFullName("System Administrator");
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setPhoneNo("0123456789");
                admin.setIcNumber("800101-10-9999");
                admin.setGender("FEMALE");
                admin.setEmail("admin@bloodbank.my");
                admin.setActive(Boolean.TRUE);
                admin.setLocked(Boolean.FALSE);
                admin.setDepartment("System Administration");

                bloodAdministratorRepository.save(admin);

                System.out.println("Default admin account created: username=admin, password=admin123");
            } else {
                System.out.println("Default admin account already exists.");
            }
        };
    }
}
