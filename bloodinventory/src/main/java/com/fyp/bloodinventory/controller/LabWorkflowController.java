package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.LabScreeningRequest;
import com.fyp.bloodinventory.service.LabWorkflowService;
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
import java.util.Objects;

@Controller
public class LabWorkflowController {

    private final LabWorkflowService labWorkflowService;
    private final SystemNotificationService notificationService;

    public LabWorkflowController(@NonNull LabWorkflowService labWorkflowService,
                                 @NonNull SystemNotificationService notificationService) {
        this.labWorkflowService = labWorkflowService;
        this.notificationService = notificationService;
    }

    @GetMapping("/lab/pending-tests")
    public String pendingTests(Model model) {
        populateTtiScreening(model);
        return "lab-tti-screening";
    }

    @GetMapping("/lab/tti-screening")
    public String ttiScreening(Model model) {
        populateTtiScreening(model);
        return "lab-tti-screening";
    }

    @PostMapping("/lab/tti-screening")
    public String recordScreening(@ModelAttribute("screeningRequest") LabScreeningRequest request,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            labWorkflowService.recordScreening(request, actorName(principal));
            notificationService.record(
                    "TTI Screening",
                    "UPDATE",
                    "Recorded lab screening for donation ID " + request.getDonationId(),
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Screening result recorded.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/lab/tti-screening";
    }

    @PostMapping("/lab/tti-screening/delete")
    public String deleteScreening(@RequestParam("testId") Long testId,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            labWorkflowService.deleteScreening(testId);
            notificationService.record(
                    "TTI Screening",
                    "DELETE",
                    "Deleted lab screening result ID " + testId,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Screening result deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/lab/tti-screening";
    }

    @GetMapping("/lab/component-status")
    public String componentStatus(Model model) {
        model.addAttribute("components", labWorkflowService.getComponentStatuses());
        model.addAttribute("componentStatuses", LabWorkflowService.LAB_COMPONENT_STATUS_OPTIONS);
        return "lab-component-status";
    }

    @PostMapping("/lab/component-status/update")
    public String updateComponentStatus(@RequestParam("componentId") Long componentId,
                                        @RequestParam("status") String status,
                                        Principal principal,
                                        RedirectAttributes redirectAttributes) {
        try {
            labWorkflowService.updateComponentStatus(componentId, status);
            notificationService.record(
                    "Component Status",
                    "UPDATE",
                    "Updated component ID " + componentId + " to " + status,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Component status updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/lab/component-status";
    }

    @GetMapping("/lab/traceability")
    public String traceability(Model model) {
        model.addAttribute("traceabilityRecords", labWorkflowService.getTraceabilityRecords());
        return "lab-traceability";
    }

    private void populateTtiScreening(Model model) {
        model.addAttribute("pendingTests", labWorkflowService.getPendingTests());
        model.addAttribute("testRecords", labWorkflowService.getTestRecords());
        model.addAttribute("ttiResults", LabWorkflowService.TTI_RESULTS);
        model.addAttribute("bloodTypeResults", LabWorkflowService.BLOOD_TYPE_RESULTS);
        model.addAttribute("finalStatuses", LabWorkflowService.FINAL_STATUSES);
        if (!model.containsAttribute("screeningRequest")) {
            model.addAttribute("screeningRequest", new LabScreeningRequest());
        }
    }

    private @NonNull String actorName(Principal principal) {
        return principal == null ? "system" : Objects.requireNonNull(principal.getName(), "Principal name must not be null.");
    }
}
