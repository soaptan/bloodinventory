package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.config.PasswordHashSupport;
import com.fyp.bloodinventory.entity.Staff;
import com.fyp.bloodinventory.repository.StaffRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final StaffRepository staffRepository;

    public CustomUserDetailsService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Staff staff = staffRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        boolean isActive = !Boolean.FALSE.equals(staff.getActive());
        boolean isLocked = Boolean.TRUE.equals(staff.getLocked());
        String storedPassword = PasswordHashSupport.normalizeStoredPassword(staff.getPassword());

        return org.springframework.security.core.userdetails.User.withUsername(staff.getUsername())
                .password(storedPassword)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + staff.getStaffType().name())))
                .disabled(!isActive)
                .accountLocked(isLocked)
                .build();
    }
}
