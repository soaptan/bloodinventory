package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.AdminDashboardStats;
import com.fyp.bloodinventory.service.AdminDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final AdminDashboardService adminDashboardService;

    public DashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        AdminDashboardStats stats = adminDashboardService.getDashboardStats();
        model.addAttribute("stats", stats);
        return "admin-dashboard";
    }

    @GetMapping("/medical/dashboard")
    public String medicalDashboard() {
        return "medical-dashboard";
    }

    @GetMapping("/lab/dashboard")
    public String labDashboard() {
        return "lab-dashboard";
    }
}