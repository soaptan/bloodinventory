package com.fyp.bloodinventory.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccessController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Authentication authentication) {
        if (hasRole(authentication, "ROLE_BLOOD_ADMINISTRATOR")) {
            return "redirect:/admin/dashboard";
        }

        if (hasRole(authentication, "ROLE_MEDICAL_STAFF")) {
            return "redirect:/medical/dashboard";
        }

        if (hasRole(authentication, "ROLE_LAB_TECHNICIAN")) {
            return "redirect:/lab/dashboard";
        }

        return "redirect:/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
