package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.StaffRegistrationRequest;
import com.fyp.bloodinventory.entity.StaffRole;
import com.fyp.bloodinventory.service.StaffService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/staff")
public class AdminStaffController {

    private final StaffService staffService;

    public AdminStaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("staffRequest", new StaffRegistrationRequest());
        model.addAttribute("roles", StaffRole.values());
        return "staff-register";
    }

    @PostMapping("/register")
    public String registerStaff(@ModelAttribute("staffRequest") StaffRegistrationRequest request,
                                Model model) {
        try {
            staffService.registerStaff(request);
            model.addAttribute("successMessage", "Staff account created successfully.");
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
        }

        model.addAttribute("roles", StaffRole.values());
        return "staff-register";
    }
}