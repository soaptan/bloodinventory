package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.MedicalDeferralRequest;
import com.fyp.bloodinventory.dto.MedicalDonationRequest;
import com.fyp.bloodinventory.dto.MedicalDonorRequest;
import com.fyp.bloodinventory.dto.MedicalSafeMatchRequest;
import com.fyp.bloodinventory.dto.MedicalTransfusionRequest;
import com.fyp.bloodinventory.service.MedicalWorkflowService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class MedicalWorkflowController {

    private final MedicalWorkflowService medicalWorkflowService;
    private final SystemNotificationService notificationService;

    public MedicalWorkflowController(@NonNull MedicalWorkflowService medicalWorkflowService,
                                     @NonNull SystemNotificationService notificationService) {
        this.medicalWorkflowService = medicalWorkflowService;
        this.notificationService = notificationService;
    }

    @GetMapping("/medical/donor-eligibility")
    public String donorEligibility(Model model) {
        populateDonorEligibility(model);
        return "medical-donor-eligibility";
    }

    @PostMapping("/medical/donor-eligibility/donors")
    public String saveDonor(@ModelAttribute("donorRequest") MedicalDonorRequest request,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.saveDonor(request);
            notificationService.record(
                    "Donor Eligibility",
                    "UPDATE",
                    "Saved donor record: " + safeLabel(request.getFullName(), request.getIcNumber()),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Donor record saved.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/donor-eligibility";
    }

    @PostMapping("/medical/donor-eligibility/deferrals")
    public String recordDeferral(@ModelAttribute("deferralRequest") MedicalDeferralRequest request,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.recordDeferral(request, actorName(principal));
            notificationService.record(
                    "Donor Eligibility",
                    "UPDATE",
                    "Recorded donor deferral for donor ID " + request.getDonorId(),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Deferral recorded.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/donor-eligibility";
    }

    @PostMapping("/medical/donor-eligibility/clear")
    public String clearDeferral(@RequestParam("donorId") Long donorId,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.clearDeferral(donorId);
            notificationService.record(
                    "Donor Eligibility",
                    "UPDATE",
                    "Cleared active deferral for donor ID " + donorId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Donor marked eligible.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/donor-eligibility";
    }

    @GetMapping("/medical/donations")
    public String donations(Model model) {
        populateDonations(model);
        return "medical-donations";
    }

    @PostMapping("/medical/donations")
    public String recordDonation(@ModelAttribute("donationRequest") MedicalDonationRequest request,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.recordDonation(request, actorName(principal));
            notificationService.record(
                    "Blood Collection",
                    "INSERT",
                    "Recorded donation session for donor ID " + request.getDonorId(),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Donation session recorded.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/donations";
    }

    @GetMapping("/medical/transfusion")
    public String transfusion(Model model) {
        populateTransfusion(model);
        return "medical-transfusion";
    }

    @PostMapping("/medical/transfusion")
    public String recordTransfusion(@ModelAttribute("transfusionRequest") MedicalTransfusionRequest request,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.recordTransfusion(request, actorName(principal));
            notificationService.record(
                    "Transfusion Request",
                    "INSERT",
                    "Recorded transfusion for component ID " + request.getComponentId(),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Transfusion event recorded.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/transfusion";
    }

    @GetMapping("/medical/components")
    public String components(@ModelAttribute("safeMatchRequest") MedicalSafeMatchRequest request, Model model) {
        populateComponents(model, request);
        return "medical-components";
    }

    @PostMapping("/medical/components/reserve")
    public String reserveComponent(@RequestParam("componentId") Long componentId,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.reserveComponent(componentId);
            notificationService.record(
                    "Safe Blood Match",
                    "UPDATE",
                    "Reserved blood component ID " + componentId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Component reserved.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/components";
    }

    @PostMapping("/medical/components/release")
    public String releaseComponent(@RequestParam("componentId") Long componentId,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.releaseComponent(componentId);
            notificationService.record(
                    "Safe Blood Match",
                    "UPDATE",
                    "Released blood component ID " + componentId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Component released.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/components";
    }

    private void populateDonorEligibility(Model model) {
        model.addAttribute("donors", medicalWorkflowService.getDonors());
        model.addAttribute("deferralReasons", medicalWorkflowService.getDeferralReasons());
        model.addAttribute("bloodGroups", MedicalWorkflowService.BLOOD_GROUPS);
        if (!model.containsAttribute("donorRequest")) {
            model.addAttribute("donorRequest", new MedicalDonorRequest());
        }
        if (!model.containsAttribute("deferralRequest")) {
            model.addAttribute("deferralRequest", new MedicalDeferralRequest());
        }
    }

    private void populateDonations(Model model) {
        model.addAttribute("donations", medicalWorkflowService.getDonationSessions());
        model.addAttribute("eligibleDonors", medicalWorkflowService.getEligibleDonors());
        model.addAttribute("storageLocations", medicalWorkflowService.getStorageLocations());
        model.addAttribute("componentTypes", MedicalWorkflowService.COMPONENT_TYPES);
        if (!model.containsAttribute("donationRequest")) {
            model.addAttribute("donationRequest", new MedicalDonationRequest());
        }
    }

    private void populateTransfusion(Model model) {
        model.addAttribute("patients", medicalWorkflowService.getPatients());
        model.addAttribute("components", medicalWorkflowService.getTransfusionReadyComponents());
        model.addAttribute("transfusionRecords", medicalWorkflowService.getTransfusionRecords());
        if (!model.containsAttribute("transfusionRequest")) {
            model.addAttribute("transfusionRequest", new MedicalTransfusionRequest());
        }
    }

    private void populateComponents(Model model, MedicalSafeMatchRequest request) {
        model.addAttribute("safeComponents", medicalWorkflowService.getSafeComponents(request));
        model.addAttribute("bloodGroups", MedicalWorkflowService.BLOOD_GROUPS);
        model.addAttribute("componentTypes", MedicalWorkflowService.COMPONENT_TYPES);
        model.addAttribute("safeMatchRequest", request);
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
