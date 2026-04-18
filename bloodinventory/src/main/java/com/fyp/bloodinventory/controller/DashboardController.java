package com.fyp.bloodinventory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
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