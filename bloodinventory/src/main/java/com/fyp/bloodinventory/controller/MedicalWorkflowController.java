package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.MedicalDeferralRequest;
import com.fyp.bloodinventory.dto.MedicalDonationRequest;
import com.fyp.bloodinventory.dto.MedicalDonorRequest;
import com.fyp.bloodinventory.dto.MedicalSafeMatchRequest;
import com.fyp.bloodinventory.dto.MedicalTransfusionRequest;
import com.fyp.bloodinventory.service.MedicalWorkflowService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import jakarta.validation.Valid;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Objects;

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

    @GetMapping("/medical/donor-eligibility/assessment")
    public String donorAssessment(@RequestParam(value = "donorId", required = false) Long donorId,
                                  Model model) {
        populateDonorAssessment(donorId, model);
        return "medical-donor-assessment";
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

        if (request.getDonorId() != null) {
            return "redirect:/medical/donor-eligibility/assessment?donorId=" + request.getDonorId();
        }
        return "redirect:/medical/donor-eligibility/assessment";
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

        if (request.getDonorId() != null) {
            return "redirect:/medical/donor-eligibility/assessment?donorId=" + request.getDonorId();
        }
        return "redirect:/medical/donor-eligibility/assessment";
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

    @PostMapping("/medical/donor-eligibility/donors/delete")
    public String deleteDonor(@RequestParam("donorId") Long donorId,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.deleteDonor(donorId);
            notificationService.record(
                    "Donor Eligibility",
                    "DELETE",
                    "Deleted donor record ID " + donorId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Donor record deleted.");
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

    @GetMapping("/medical/donations/record")
    public String donationRecord(Model model) {
        populateDonationForm(model);
        return "medical-donation-record";
    }

    @PostMapping("/medical/donations")
    public String recordDonation(@Valid @ModelAttribute("donationRequest") MedicalDonationRequest request,
                                 BindingResult bindingResult,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the highlighted donation fields.");
            redirectAttributes.addFlashAttribute("donationRequest", request);
            redirectAttributes.addFlashAttribute(
                    BindingResult.MODEL_KEY_PREFIX + "donationRequest",
                    bindingResult
            );
            return "redirect:/medical/donations/record";
        }

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

        return "redirect:/medical/donations/record";
    }

    @PostMapping("/medical/donations/update")
    public String updateDonation(@RequestParam("donationId") Long donationId,
                                 @RequestParam("collectionTimestamp") String collectionTimestamp,
                                 @RequestParam("locationId") Long locationId,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.updateDonation(donationId, collectionTimestamp, locationId);
            notificationService.record(
                    "Blood Collection",
                    "UPDATE",
                    "Updated donation session ID " + donationId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Donation session updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/donations";
    }

    @PostMapping("/medical/donations/delete")
    public String deleteDonation(@RequestParam("donationId") Long donationId,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.deleteDonation(donationId);
            notificationService.record(
                    "Blood Collection",
                    "DELETE",
                    "Deleted donation session ID " + donationId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Donation session deleted.");
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

    @GetMapping("/medical/transfusion/record")
    public String transfusionRecord(Model model) {
        populateTransfusionForm(model);
        return "medical-transfusion-record";
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

        return "redirect:/medical/transfusion/record";
    }

    @PostMapping("/medical/transfusion/update")
    public String updateTransfusion(@RequestParam("componentId") Long componentId,
                                    @ModelAttribute("transfusionRequest") MedicalTransfusionRequest request,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.updateTransfusion(componentId, request);
            notificationService.record(
                    "Transfusion Request",
                    "UPDATE",
                    "Updated transfusion for component ID " + componentId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Transfusion event updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/medical/transfusion";
    }

    @PostMapping("/medical/transfusion/delete")
    public String deleteTransfusion(@RequestParam("componentId") Long componentId,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            medicalWorkflowService.deleteTransfusion(componentId);
            notificationService.record(
                    "Transfusion Request",
                    "DELETE",
                    "Deleted transfusion for component ID " + componentId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Transfusion event deleted.");
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
        model.addAttribute("bloodGroups", MedicalWorkflowService.BLOOD_GROUPS);
    }

    private void populateDonorAssessment(Long donorId, Model model) {
        var donors = medicalWorkflowService.getDonors();
        model.addAttribute("donors", donors);
        model.addAttribute("deferralReasons", medicalWorkflowService.getDeferralReasons());
        model.addAttribute("bloodGroups", MedicalWorkflowService.BLOOD_GROUPS);

        MedicalDonorRequest donorRequest = new MedicalDonorRequest();
        MedicalDeferralRequest deferralRequest = new MedicalDeferralRequest();
        String selectedDonorName = "";

        if (donorId != null) {
            var selectedDonor = donors.stream()
                    .filter(donor -> Objects.equals(donor.getDonorId(), donorId))
                    .findFirst()
                    .orElse(null);

            if (selectedDonor == null) {
                model.addAttribute("errorMessage", "The selected donor record could not be found.");
            } else {
                donorRequest.setDonorId(selectedDonor.getDonorId());
                donorRequest.setFullName(selectedDonor.getFullName());
                donorRequest.setIcNumber(selectedDonor.getIcNumber());
                donorRequest.setBloodGroup(selectedDonor.getBloodGroup());
                deferralRequest.setDonorId(selectedDonor.getDonorId());
                selectedDonorName = selectedDonor.getFullName();
            }
        }

        if (!model.containsAttribute("donorRequest")) {
            model.addAttribute("donorRequest", donorRequest);
        }
        if (!model.containsAttribute("deferralRequest")) {
            model.addAttribute("deferralRequest", deferralRequest);
        }
        model.addAttribute("selectedDonorName", selectedDonorName);
    }

    private void populateDonations(Model model) {
        model.addAttribute("donations", medicalWorkflowService.getDonationSessions());
        model.addAttribute("storageLocations", medicalWorkflowService.getStorageLocations());
        model.addAttribute("bloodGroups", MedicalWorkflowService.BLOOD_GROUPS);
    }

    private void populateDonationForm(Model model) {
        model.addAttribute("donationDonors", medicalWorkflowService.getDonors());
        model.addAttribute("storageLocations", medicalWorkflowService.getStorageLocations());
        model.addAttribute("componentTypes", MedicalWorkflowService.COMPONENT_TYPES);
        if (!model.containsAttribute("donationRequest")) {
            model.addAttribute("donationRequest", new MedicalDonationRequest());
        }
    }

    private void populateTransfusion(Model model) {
        model.addAttribute("transfusionRecords", medicalWorkflowService.getTransfusionRecords());
        model.addAttribute("bloodGroups", MedicalWorkflowService.BLOOD_GROUPS);
        model.addAttribute("componentTypes", MedicalWorkflowService.COMPONENT_TYPES);
    }

    private void populateTransfusionForm(Model model) {
        model.addAttribute("patients", medicalWorkflowService.getPatients());
        model.addAttribute("components", medicalWorkflowService.getTransfusionReadyComponents());
        if (!model.containsAttribute("transfusionRequest")) {
            model.addAttribute("transfusionRequest", new MedicalTransfusionRequest());
        }
    }

    private void populateComponents(Model model, MedicalSafeMatchRequest request) {
        var safeComponents = medicalWorkflowService.getSafeComponents(request);
        model.addAttribute("safeComponents", safeComponents);
        model.addAttribute("safeMatchLocations", safeComponents.stream()
                .map(component -> component.getLocationDescription())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(location -> !location.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
        model.addAttribute("bloodGroups", MedicalWorkflowService.BLOOD_GROUPS);
        model.addAttribute("componentTypes", MedicalWorkflowService.COMPONENT_TYPES);
        model.addAttribute("safeMatchRequest", request);
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
}
