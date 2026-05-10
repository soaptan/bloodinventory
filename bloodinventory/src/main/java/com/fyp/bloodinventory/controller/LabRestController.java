package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.service.LabWorkflowService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/lab")
public class LabRestController {

    private final LabWorkflowService labWorkflowService;
    private final SystemNotificationService notificationService;

    public LabRestController(@NonNull LabWorkflowService labWorkflowService,
                             @NonNull SystemNotificationService notificationService) {
        this.labWorkflowService = labWorkflowService;
        this.notificationService = notificationService;
    }

    @PostMapping("/approve/{donationId}")
    public ResponseEntity<Map<String, Object>> approvePendingDonation(@PathVariable Long donationId,
                                                                      Principal principal) {
        try {
            String username = principal == null ? null : principal.getName();
            labWorkflowService.approvePendingDonation(donationId, username);
            notificationService.record(
                    "Pending Test Queue",
                    "UPDATE",
                    "Approved pending lab screening for donation ID " + donationId,
                    username
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "donationId", donationId,
                    "message", "Donation #" + donationId + " marked as SAFE."
            ));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "donationId", donationId,
                    "message", ex.getMessage() == null ? "Unable to approve the pending lab test." : ex.getMessage()
            ));
        }
    }
}
