package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.StaffManagementRequest;
import com.fyp.bloodinventory.dto.StaffPasswordChangeRequest;
import com.fyp.bloodinventory.dto.StaffProfileDto;
import com.fyp.bloodinventory.dto.StaffProfileUpdateRequest;
import com.fyp.bloodinventory.dto.StaffRegistrationRequest;
import com.fyp.bloodinventory.entity.StaffRole;
import com.fyp.bloodinventory.service.StaffService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataAccessException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;
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
        populateOwnProfilePage(actorName(principal), model, null);
        return "staff-profile";
    }

    @GetMapping("/admin/staff/profiles")
    public String redirectLegacyStaffProfilesRoute() {
        return "redirect:/admin/staff/management";
    }

    @GetMapping("/admin/staff/management")
    public String showStaffManagementPage(Model model, Principal principal) {
        populateStaffManagementPage(model, actorName(principal));
        return "staff-profiles";
    }

    @PostMapping("/admin/staff/register")
    public String registerStaff(@Valid @ModelAttribute("staffRequest") StaffRegistrationRequest request,
                                BindingResult bindingResult,
                                @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {

        validateRegistrationRoleFields(request, bindingResult);
        validateRegistrationPhoto(photoFile, bindingResult);

        if (bindingResult.hasErrors()) {
            request.setPassword(null);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the highlighted staff registration fields.");
            redirectAttributes.addFlashAttribute("staffRequest", request);
            redirectAttributes.addFlashAttribute(
                    BindingResult.MODEL_KEY_PREFIX + "staffRequest",
                    bindingResult
            );
            redirectAttributes.addFlashAttribute("openRegisterModal", true);
            return "redirect:/admin/staff/management";
        }

        try {
            staffService.registerStaff(request, photoFile);
            notificationService.record(
                    "Staff Management",
                    "INSERT",
                    "Created staff account for " + safeLabel(request.getFullName(), request.getUsername()),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Staff account created successfully.");
        } catch (DataAccessException e) {
            preserveRegistrationFailure(request, redirectAttributes,
                    "The staff account could not be saved. Check that the username, IC number, email, and role credentials are unique.");
        } catch (IllegalArgumentException e) {
            preserveRegistrationFailure(request, redirectAttributes, safeLabel(e.getMessage(), "The registration details are invalid."));
        } catch (RuntimeException e) {
            preserveRegistrationFailure(request, redirectAttributes,
                    safeLabel(e.getMessage(), "The staff account could not be created."));
        } catch (Exception e) {
            preserveRegistrationFailure(request, redirectAttributes,
                    "The staff account could not be created right now. Please try again.");
        }

        return "redirect:/admin/staff/management";
    }

    private void preserveRegistrationFailure(StaffRegistrationRequest request,
                                             RedirectAttributes redirectAttributes,
                                             String message) {
            request.setPassword(null);
            redirectAttributes.addFlashAttribute("errorMessage", message);
            redirectAttributes.addFlashAttribute("staffRequest", request);
            redirectAttributes.addFlashAttribute("openRegisterModal", true);
    }

    private void validateRegistrationRoleFields(StaffRegistrationRequest request, BindingResult bindingResult) {
        if (request.getStaffType() == StaffRole.MEDICAL_STAFF) {
            rejectBlank(bindingResult, "licenseNo", request.getLicenseNo(), "Medical license number is required.");
            rejectBlank(bindingResult, "position", request.getPosition(), "Clinical position is required.");
        } else if (request.getStaffType() == StaffRole.LAB_TECHNICIAN) {
            rejectBlank(bindingResult, "certificationNo", request.getCertificationNo(),
                    "Laboratory certification number is required.");
        } else if (request.getStaffType() == StaffRole.BLOOD_ADMINISTRATOR) {
            rejectBlank(bindingResult, "department", request.getDepartment(), "Department is required.");
        }
    }

    private void validateRegistrationPhoto(MultipartFile photoFile, BindingResult bindingResult) {
        if (photoFile == null || photoFile.isEmpty()) {
            return;
        }
        if (photoFile.getSize() > 2L * 1024L * 1024L) {
            bindingResult.reject("photoFile.size", "Profile photo must not exceed 2 MB.");
        }
        String contentType = photoFile.getContentType();
        if (!"image/jpeg".equalsIgnoreCase(contentType) && !"image/png".equalsIgnoreCase(contentType)) {
            bindingResult.reject("photoFile.type", "Profile photo must be a JPG or PNG image.");
        }
    }

    private void rejectBlank(BindingResult bindingResult, String field, String value, String message) {
        if (value == null || value.isBlank()) {
            bindingResult.rejectValue(field, "required", message);
        }
    }

    @PostMapping("/admin/staff/{id}/update")
    public String updateStaff(@PathVariable("id") Long id,
                              @ModelAttribute("editStaffRequest") StaffManagementRequest request,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {

        try {
            Long staffId = Objects.requireNonNull(id, "Staff ID must not be null.");
            staffService.updateStaff(staffId, request, actorName(principal));
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

    @PostMapping({"/admin/staff/{id}/archive", "/admin/staff/{id}/delete"})
    public String archiveStaff(@PathVariable("id") Long id,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        try {
            Long staffId = Objects.requireNonNull(id, "Staff ID must not be null.");
            staffService.archiveStaff(staffId, actorName(principal));
            notificationService.record(
                    "Staff Management",
                    "ARCHIVE",
                    "Archived staff account ID " + staffId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    "Staff account archived and blocked from signing in.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/staff/management";
    }

    @PostMapping({"/admin/staff/archive-selected", "/admin/staff/delete-selected"})
    public String archiveSelectedStaff(@RequestParam(value = "staffIds", required = false) List<Long> staffIds,
                                       Principal principal,
                                       RedirectAttributes redirectAttributes) {
        try {
            int archivedCount = staffService.archiveSelectedStaff(staffIds, actorName(principal));
            notificationService.record(
                    "Staff Management",
                    "ARCHIVE",
                    "Archived " + archivedCount + " selected staff account"
                            + (archivedCount == 1 ? "" : "s"),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    archivedCount == 1
                            ? "Selected staff account archived successfully."
                            : archivedCount + " selected staff accounts archived successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/staff/management";
    }

    @PostMapping("/admin/staff/{id}/restore")
    public String restoreStaff(@PathVariable("id") Long id,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        try {
            Long staffId = Objects.requireNonNull(id, "Staff ID must not be null.");
            staffService.restoreStaff(staffId);
            notificationService.record(
                    "Staff Management",
                    "RESTORE",
                    "Restored staff account ID " + staffId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    "Staff account restored and allowed to sign in again.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/staff/management";
    }

    @PostMapping("/admin/staff/restore-selected")
    public String restoreSelectedStaff(@RequestParam(value = "staffIds", required = false) List<Long> staffIds,
                                       Principal principal,
                                       RedirectAttributes redirectAttributes) {
        try {
            int restoredCount = staffService.restoreSelectedStaff(staffIds);
            notificationService.record(
                    "Staff Management",
                    "RESTORE",
                    "Restored " + restoredCount + " selected staff account"
                            + (restoredCount == 1 ? "" : "s"),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    restoredCount == 1
                            ? "Selected staff account restored successfully."
                            : restoredCount + " selected staff accounts restored successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/staff/management";
    }

    @PostMapping(value = "/profile", produces = MediaType.TEXT_HTML_VALUE)
    public String updateOwnProfile(Principal principal,
                                   @ModelAttribute("profileForm") StaffProfileUpdateRequest profileForm,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        try {
            String username = actorName(principal);
            staffService.updateOwnProfile(username, profileForm);
            notificationService.record(
                    "Profile",
                    "UPDATE",
                    "Updated personal profile for " + safeLabel(profileForm.getFullName(), username),
                    username
            );
            redirectAttributes.addFlashAttribute("successMessage", "Profile details updated successfully.");
            return "redirect:/profile";
        } catch (Exception e) {
            populateOwnProfilePage(actorName(principal), model, profileForm);
            model.addAttribute("errorMessage", e.getMessage());
            return "staff-profile";
        }
    }

    @PostMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateOwnProfileAsync(
            Principal principal,
            @ModelAttribute("profileForm") StaffProfileUpdateRequest profileForm) {

        try {
            String username = actorName(principal);
            staffService.updateOwnProfile(username, profileForm);
            notificationService.record(
                    "Profile",
                    "UPDATE",
                    "Updated personal profile for " + safeLabel(profileForm.getFullName(), username),
                    username
            );
            return ResponseEntity.ok(successResponse(
                    "Profile updated successfully.",
                    Map.of(
                            "fullName", safeLabel(profileForm.getFullName(), username),
                            "email", safeLabel(profileForm.getEmail(), "Not provided"),
                            "phoneNo", safeLabel(profileForm.getPhoneNo(), "Not provided")
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    @PostMapping(value = "/profile/password", produces = MediaType.TEXT_HTML_VALUE)
    public String updateOwnPassword(Principal principal,
                                    @ModelAttribute StaffPasswordChangeRequest passwordForm,
                                    RedirectAttributes redirectAttributes) {

        try {
            String username = actorName(principal);
            staffService.updateOwnPassword(
                    username,
                    passwordForm.getCurrentPassword(),
                    passwordForm.getNewPassword(),
                    passwordForm.getConfirmPassword()
            );
            notificationService.record(
                    "Profile",
                    "UPDATE",
                    "Changed personal account password for " + username,
                    username
            );
            redirectAttributes.addFlashAttribute("successMessage", "Password updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping(value = "/profile/password", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateOwnPasswordAsync(
            Principal principal,
            @ModelAttribute StaffPasswordChangeRequest passwordForm) {

        try {
            String username = actorName(principal);
            staffService.updateOwnPassword(
                    username,
                    passwordForm.getCurrentPassword(),
                    passwordForm.getNewPassword(),
                    passwordForm.getConfirmPassword()
            );
            notificationService.record(
                    "Profile",
                    "UPDATE",
                    "Changed personal account password for " + username,
                    username
            );
            return ResponseEntity.ok(successResponse("Password updated successfully.", Map.of()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    @PostMapping({"/admin/staff/profile/photo", "/profile/photo"})
    public String updateOwnStaffPhoto(Principal principal,
                                      @RequestParam("photoFile") MultipartFile photoFile,
                                      RedirectAttributes redirectAttributes) {

        try {
            String username = actorName(principal);
            staffService.updateStaffPhotoByUsername(username, photoFile);
            notificationService.record(
                    "Profile",
                    "UPDATE",
                    "Updated profile photo for " + username,
                    username
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
        model.addAttribute("archivedCount", staffProfiles.stream()
                .filter(profile -> Boolean.FALSE.equals(profile.getActive()))
                .count());
    }

    private void populateOwnProfilePage(@NonNull String username, Model model, StaffProfileUpdateRequest profileForm) {
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

    private @NonNull String actorName(Principal principal) {
        return principal == null ? "system" : Objects.requireNonNull(principal.getName(), "Principal name must not be null.");
    }

    private String safeLabel(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }

        return fallback == null || fallback.isBlank() ? "record" : fallback.trim();
    }

    private Map<String, Object> successResponse(String message, Map<String, Object> extraValues) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.putAll(extraValues);
        return response;
    }

    private Map<String, Object> errorResponse(String message) {
        return Map.of(
                "success", false,
                "message", safeLabel(message, "Request failed.")
        );
    }
}
