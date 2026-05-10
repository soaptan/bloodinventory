package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.StaffManagementRequest;
import com.fyp.bloodinventory.dto.StaffProfileDto;
import com.fyp.bloodinventory.dto.StaffProfileUpdateRequest;
import com.fyp.bloodinventory.dto.StaffRegistrationRequest;
import com.fyp.bloodinventory.entity.StaffRole;
import com.fyp.bloodinventory.service.StaffService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

@Controller
public class AdminStaffController {

    private final StaffService staffService;
    private final SystemNotificationService notificationService;

    public AdminStaffController(StaffService staffService,
                                SystemNotificationService notificationService) {
        this.staffService = staffService;
        this.notificationService = notificationService;
    }

    @GetMapping("/admin/staff/register")
    public String showRegisterStaffPage(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("openRegisterModal", true);
        return "redirect:/admin/staff/management";
    }

    @GetMapping("/admin/staff/profile")
    public String redirectAdminProfileRoute() {
        return "redirect:/profile";
    }

    @GetMapping("/profile")
    public String showOwnStaffProfilePage(Principal principal, Model model) {
        populateOwnProfilePage(principal.getName(), model, null);
        return "staff-profile";
    }

    @GetMapping("/admin/staff/profiles")
    public String redirectLegacyStaffProfilesRoute() {
        return "redirect:/admin/staff/management";
    }

    @GetMapping("/admin/staff/management")
    public String showStaffManagementPage(Model model, Principal principal) {
        populateStaffManagementPage(model, principal.getName());
        return "staff-profiles";
    }

    @PostMapping("/admin/staff/register")
    public String registerStaff(@ModelAttribute("staffRequest") StaffRegistrationRequest request,
                                @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {

        try {
            staffService.registerStaff(request, photoFile);
            notificationService.record(
                    "Staff Management",
                    "INSERT",
                    "Created staff account for " + safeLabel(request.getFullName(), request.getUsername()),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Staff account created successfully.");
        } catch (Exception e) {
            request.setPassword(null);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("staffRequest", request);
            redirectAttributes.addFlashAttribute("openRegisterModal", true);
        }

        return "redirect:/admin/staff/management";
    }

    @PostMapping("/admin/staff/{id}/update")
    public String updateStaff(@PathVariable("id") Long id,
                              @ModelAttribute("editStaffRequest") StaffManagementRequest request,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {

        try {
            Long staffId = Objects.requireNonNull(id, "Staff ID must not be null.");
            staffService.updateStaff(staffId, request, principal.getName());
            notificationService.record(
                    "Staff Management",
                    "UPDATE",
                    "Updated staff account for " + safeLabel(request.getFullName(), "ID " + staffId),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Staff profile updated successfully.");
        } catch (Exception e) {
            request.setPassword(null);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("editingStaffId", id);
            redirectAttributes.addFlashAttribute("editStaffRequest", request);
        }

        return "redirect:/admin/staff/management";
    }

    @PostMapping("/admin/staff/{id}/delete")
    public String deleteStaff(@PathVariable("id") Long id,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        try {
            Long staffId = Objects.requireNonNull(id, "Staff ID must not be null.");
            staffService.deleteStaff(staffId, principal.getName());
            notificationService.record(
                    "Staff Management",
                    "DELETE",
                    "Deleted staff account ID " + staffId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Staff profile deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/staff/management";
    }

    @PostMapping("/admin/staff/delete-selected")
    public String deleteSelectedStaff(@RequestParam(value = "staffIds", required = false) List<Long> staffIds,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            int deletedCount = staffService.deleteSelectedStaff(staffIds, principal.getName());
            notificationService.record(
                    "Staff Management",
                    "DELETE",
                    "Deleted " + deletedCount + " selected staff account"
                            + (deletedCount == 1 ? "" : "s"),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    deletedCount == 1
                            ? "Selected staff profile deleted successfully."
                            : deletedCount + " selected staff profiles deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/staff/management";
    }

    @PostMapping("/profile")
    public String updateOwnProfile(Principal principal,
                                   @ModelAttribute("profileForm") StaffProfileUpdateRequest profileForm,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        try {
            staffService.updateOwnProfile(principal.getName(), profileForm);
            notificationService.record(
                    "Profile",
                    "UPDATE",
                    "Updated personal profile for " + safeLabel(profileForm.getFullName(), principal.getName()),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Profile details updated successfully.");
            return "redirect:/profile";
        } catch (Exception e) {
            populateOwnProfilePage(principal.getName(), model, profileForm);
            model.addAttribute("errorMessage", e.getMessage());
            return "staff-profile";
        }
    }

    @PostMapping({"/admin/staff/profile/photo", "/profile/photo"})
    public String updateOwnStaffPhoto(Principal principal,
                                      @RequestParam("photoFile") MultipartFile photoFile,
                                      RedirectAttributes redirectAttributes) {

        try {
            staffService.updateStaffPhotoByUsername(principal.getName(), photoFile);
            notificationService.record(
                    "Profile",
                    "UPDATE",
                    "Updated profile photo for " + principal.getName(),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Profile photo updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping("/admin/staff/{id}/photo")
    public String updateStaffPhoto(@PathVariable("id") Long id,
                                   @RequestParam("photoFile") MultipartFile photoFile,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {

        try {
            Long staffId = Objects.requireNonNull(id, "Staff ID must not be null.");
            staffService.updateStaffPhoto(staffId, photoFile);
            notificationService.record(
                    "Staff Management",
                    "UPDATE",
                    "Updated profile photo for staff ID " + staffId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Profile photo updated successfully.");
            redirectAttributes.addFlashAttribute("editingStaffId", id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("editingStaffId", id);
        }

        return "redirect:/admin/staff/management";
    }

    private void populateStaffManagementPage(Model model, String currentUsername) {
        if (!model.containsAttribute("staffRequest")) {
            model.addAttribute("staffRequest", new StaffRegistrationRequest());
        }

        List<StaffProfileDto> staffProfiles = staffService.getAllStaffProfiles();

        model.addAttribute("roles", StaffRole.values());
        model.addAttribute("currentUsername", currentUsername);
        model.addAttribute("staffProfiles", staffProfiles);
        model.addAttribute("staffCount", staffProfiles.size());
        model.addAttribute("administratorCount", staffProfiles.stream()
                .filter(profile -> "administrator".equals(profile.getRoleAccentClass()))
                .count());
        model.addAttribute("medicalCount", staffProfiles.stream()
                .filter(profile -> "medical".equals(profile.getRoleAccentClass()))
                .count());
        model.addAttribute("labCount", staffProfiles.stream()
                .filter(profile -> "lab".equals(profile.getRoleAccentClass()))
                .count());
        model.addAttribute("inactiveCount", staffProfiles.stream()
                .filter(profile -> Boolean.FALSE.equals(profile.getActive()))
                .count());
    }

    private void populateOwnProfilePage(String username, Model model, StaffProfileUpdateRequest profileForm) {
        StaffProfileDto profile = staffService.getStaffProfileByUsername(username);
        model.addAttribute("profile", profile);
        model.addAttribute("profileForm",
                profileForm != null ? profileForm : staffService.getProfileUpdateRequestByUsername(username));

        switch (profile.getRoleAccentClass()) {
            case "administrator" -> {
                model.addAttribute("sidebarRoleLabel", "Blood Administrator Panel");
                model.addAttribute("sidebarRoleDescription",
                        "Centralized navigation for staff, storage, inventory monitoring, alerts, and reports.");
                model.addAttribute("dashboardPath", "/admin/dashboard");
                model.addAttribute("dashboardLabel", "Administrator Dashboard");
            }
            case "medical" -> {
                model.addAttribute("sidebarRoleLabel", "Medical Staff Panel");
                model.addAttribute("sidebarRoleDescription",
                        "Clinical workflows for donor eligibility, blood collection, and transfusion requests.");
                model.addAttribute("dashboardPath", "/medical/dashboard");
                model.addAttribute("dashboardLabel", "Medical Dashboard");
            }
            default -> {
                model.addAttribute("sidebarRoleLabel", "Lab Technician Panel");
                model.addAttribute("sidebarRoleDescription",
                        "Laboratory tools for queue management, screening validation, and component traceability.");
                model.addAttribute("dashboardPath", "/lab/dashboard");
                model.addAttribute("dashboardLabel", "Lab Dashboard");
            }
        }

        model.addAttribute("sidebarActiveMenu", "");
    }

    private String actorName(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    private String safeLabel(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }

        return fallback == null || fallback.isBlank() ? "record" : fallback.trim();
    }
}
