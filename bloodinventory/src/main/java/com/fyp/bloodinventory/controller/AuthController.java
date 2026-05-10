package com.fyp.bloodinventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final JdbcTemplate jdbcTemplate;

    public AuthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/login")
    public String loginPage(HttpServletRequest request) {
        // Ensure the session exists before Thymeleaf reaches the form tag.
        request.getSession();
        return "login";
    }

    @GetMapping("/forgot-username")
    public String forgotUsernamePage(HttpServletRequest request) {
        request.getSession();
        return "forgot-username";
    }

    @PostMapping("/forgot-username")
    public String recoverUsername(@RequestParam("email") String email,
                                  @RequestParam("icNumber") String icNumber,
                                  HttpServletRequest request,
                                  Model model) {
        request.getSession();

        String normalizedEmail = normalize(email);
        String normalizedIcNumber = normalize(icNumber);
        if (normalizedEmail == null || normalizedIcNumber == null) {
            model.addAttribute("errorMessage", "Please enter both email and IC number.");
            return "forgot-username";
        }

        var usernames = jdbcTemplate.queryForList("""
                SELECT username
                FROM staff
                WHERE LOWER(email) = LOWER(?)
                  AND ic_number = ?
                  AND is_active = TRUE
                ORDER BY staff_id ASC
                """, String.class, normalizedEmail, normalizedIcNumber);

        if (usernames.isEmpty()) {
            model.addAttribute("errorMessage", "No active staff account matched those details.");
        } else {
            model.addAttribute("successMessage", "Your username is: " + usernames.get(0));
        }

        model.addAttribute("email", normalizedEmail);
        model.addAttribute("icNumber", normalizedIcNumber);
        return "forgot-username";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(HttpServletRequest request) {
        request.getSession();
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String resetPassword(@RequestParam("username") String username,
                                @RequestParam("email") String email,
                                @RequestParam("icNumber") String icNumber,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("confirmPassword") String confirmPassword,
                                HttpServletRequest request,
                                Model model) {
        request.getSession();

        String normalizedUsername = normalize(username);
        String normalizedEmail = normalize(email);
        String normalizedIcNumber = normalize(icNumber);
        String normalizedNewPassword = normalize(newPassword);
        String normalizedConfirmPassword = normalize(confirmPassword);

        if (normalizedUsername == null || normalizedEmail == null || normalizedIcNumber == null
                || normalizedNewPassword == null || normalizedConfirmPassword == null) {
            model.addAttribute("errorMessage", "Please complete all password reset fields.");
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        if (normalizedNewPassword.length() < 8) {
            model.addAttribute("errorMessage", "New password must contain at least 8 characters.");
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        if (!normalizedNewPassword.equals(normalizedConfirmPassword)) {
            model.addAttribute("errorMessage", "New password and confirmation do not match.");
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        int updatedRows = jdbcTemplate.update("""
                UPDATE staff
                SET password = ?
                WHERE username = ?
                  AND LOWER(email) = LOWER(?)
                  AND ic_number = ?
                  AND is_active = TRUE
                """, normalizedNewPassword, normalizedUsername, normalizedEmail, normalizedIcNumber);

        if (updatedRows == 0) {
            model.addAttribute("errorMessage", "No active staff account matched those details.");
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        model.addAttribute("successMessage", "Password reset successfully. You can sign in with the new password.");
        return "forgot-password";
    }

    private void preservePasswordResetInputs(Model model, String username, String email, String icNumber) {
        model.addAttribute("username", username);
        model.addAttribute("email", email);
        model.addAttribute("icNumber", icNumber);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
