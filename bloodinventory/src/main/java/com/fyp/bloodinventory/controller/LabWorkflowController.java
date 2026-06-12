package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.LabScreeningRequest;
import com.fyp.bloodinventory.dto.LabTraceabilityDto;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
        model.addAttribute("allComponentStatuses", LabWorkflowService.COMPONENT_STATUSES);
        model.addAttribute("finalStatuses", LabWorkflowService.FINAL_STATUSES);
        model.addAttribute("componentTypes", java.util.List.of("RBC", "PLASMA", "PLATELET"));
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
        List<LabTraceabilityDto> records = labWorkflowService.getTraceabilityRecords();
        long linkedLabTests = records.stream()
                .filter(record -> record.getTestId() != null)
                .count();
        long storedRecords = records.stream()
                .filter(record -> hasText(record.getLocationDescription()))
                .count();
        long finalMovements = records.stream()
                .filter(record -> record.getTransfusionTimestamp() != null
                        || "Transfused".equalsIgnoreCase(record.getLifecycleStage())
                        || "Discarded".equalsIgnoreCase(record.getLifecycleStage()))
                .count();
        long pendingRecords = records.stream()
                .filter(record -> "Pending screening".equalsIgnoreCase(record.getLifecycleStage()))
                .count();

        model.addAttribute("traceabilityRecords", records);
        model.addAttribute("traceabilityTotal", records.size());
        model.addAttribute("traceabilityLinkedLabTests", linkedLabTests);
        model.addAttribute("traceabilityStoredRecords", storedRecords);
        model.addAttribute("traceabilityFinalMovements", finalMovements);
        model.addAttribute("traceabilityPendingRecords", pendingRecords);
        model.addAttribute("traceabilityUnlinkedLabTests", records.size() - linkedLabTests);
        model.addAttribute("traceabilityMissingStorageRecords", records.size() - storedRecords);
        model.addAttribute("traceabilityInProgressMovements", records.size() - finalMovements);
        model.addAttribute("traceabilityLabCoveragePercent", percent(linkedLabTests, records.size()));
        model.addAttribute("traceabilityStorageCoveragePercent", percent(storedRecords, records.size()));
        model.addAttribute("traceabilityFinalCoveragePercent", percent(finalMovements, records.size()));
        model.addAttribute("traceabilityStages", distinctTraceabilityValues(records, LabTraceabilityDto::getLifecycleStage));
        model.addAttribute("traceabilityTypes", distinctTraceabilityValues(records, LabTraceabilityDto::getComponentType));
        model.addAttribute("traceabilityLabStatuses", distinctTraceabilityValues(records, LabTraceabilityDto::getLabFinalStatus));
        model.addAttribute("traceabilityBloodGroups", distinctTraceabilityValues(records, LabTraceabilityDto::getDonorBloodGroup));
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

    private List<String> distinctTraceabilityValues(List<LabTraceabilityDto> records,
                                                    Function<LabTraceabilityDto, String> extractor) {
        return records.stream()
                .map(extractor)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private long percent(long value, long total) {
        if (total <= 0) {
            return 0;
        }

        return Math.round((value * 100.0) / total);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
