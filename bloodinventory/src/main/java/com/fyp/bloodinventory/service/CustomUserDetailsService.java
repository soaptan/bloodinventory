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
import java.util.regex.Pattern;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    private final StaffRepository staffRepository;

    public CustomUserDetailsService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new UsernameNotFoundException("Invalid credentials.");
        }

        Staff staff = staffRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));

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
