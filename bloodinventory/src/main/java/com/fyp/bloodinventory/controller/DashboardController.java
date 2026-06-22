package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.AdminDashboardStats;
import com.fyp.bloodinventory.dto.AuditTrailDto;
import com.fyp.bloodinventory.dto.AvailableStockDto;
import com.fyp.bloodinventory.dto.DashboardChartSegmentDto;
import com.fyp.bloodinventory.dto.DeferralRuleRequest;
import com.fyp.bloodinventory.dto.LabTestQueueDto;
import com.fyp.bloodinventory.dto.MedicalComponentDto;
import com.fyp.bloodinventory.dto.MedicalDashboardSummaryDto;
import com.fyp.bloodinventory.dto.MedicalDonationDto;
import com.fyp.bloodinventory.dto.NearExpiryComponentDto;
import com.fyp.bloodinventory.dto.ReportsSummaryDto;
import com.fyp.bloodinventory.dto.StaffRoleTotalDto;
import com.fyp.bloodinventory.dto.StorageLocationRequest;
import com.fyp.bloodinventory.dto.SystemNotificationDto;
import com.fyp.bloodinventory.service.AdminDashboardService;
import com.fyp.bloodinventory.service.AuditTrailService;
import com.fyp.bloodinventory.service.DeferralRuleService;
import com.fyp.bloodinventory.service.InventoryMonitorService;
import com.fyp.bloodinventory.service.LabWorkflowService;
import com.fyp.bloodinventory.service.MedicalWorkflowService;
import com.fyp.bloodinventory.service.ReportsAlertService;
import com.fyp.bloodinventory.service.StaffService;
import com.fyp.bloodinventory.service.StorageLocationService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Controller
public class DashboardController {

    private static final Map<String, String> LAB_CHART_TONES = Map.ofEntries(
            Map.entry("PASSED", "good"),
            Map.entry("SAFE", "good"),
            Map.entry("NEGATIVE", "good"),
            Map.entry("MATCHED", "good"),
            Map.entry("AVAILABLE", "good"),
            Map.entry("FAILED", "bad"),
            Map.entry("POSITIVE", "bad"),
            Map.entry("NOT MATCHED", "bad"),
            Map.entry("MISMATCHED", "bad"),
            Map.entry("DISCARDED", "bad"),
            Map.entry("QUARANTINED", "warn"),
            Map.entry("PENDING", "warn"),
            Map.entry("PENDING SCREENING", "warn"),
            Map.entry("O+", "info"),
            Map.entry("O-", "info"),
            Map.entry("A+", "accent"),
            Map.entry("A-", "accent"),
            Map.entry("B+", "purple"),
            Map.entry("B-", "purple"),
            Map.entry("AB+", "good"),
            Map.entry("AB-", "good")
    );

    private final AdminDashboardService adminDashboardService;
    private final DeferralRuleService deferralRuleService;
    private final StorageLocationService storageLocationService;
    private final InventoryMonitorService inventoryMonitorService;
    private final MedicalWorkflowService medicalWorkflowService;
    private final LabWorkflowService labWorkflowService;
    private final ReportsAlertService reportsAlertService;
    private final AuditTrailService auditTrailService;
    private final StaffService staffService;
    private final SystemNotificationService notificationService;

    public DashboardController(AdminDashboardService adminDashboardService,
                               DeferralRuleService deferralRuleService,
                               StorageLocationService storageLocationService,
                               InventoryMonitorService inventoryMonitorService,
                               MedicalWorkflowService medicalWorkflowService,
                               LabWorkflowService labWorkflowService,
                               ReportsAlertService reportsAlertService,
                               AuditTrailService auditTrailService,
                               StaffService staffService,
                               SystemNotificationService notificationService) {
        this.adminDashboardService = adminDashboardService;
        this.deferralRuleService = deferralRuleService;
        this.storageLocationService = storageLocationService;
        this.inventoryMonitorService = inventoryMonitorService;
        this.medicalWorkflowService = medicalWorkflowService;
        this.labWorkflowService = labWorkflowService;
        this.reportsAlertService = reportsAlertService;
        this.auditTrailService = auditTrailService;
        this.staffService = staffService;
        this.notificationService = notificationService;
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        AdminDashboardStats stats = adminDashboardService.getDashboardStats();
        model.addAttribute("stats", stats);
        model.addAttribute("summaryMetrics", adminDashboardService.getSummaryMetrics());
        model.addAttribute("inventorySummary", inventoryMonitorService.getInventorySummary());
        model.addAttribute("componentStatusList", inventoryMonitorService.getComponentStatusSummary());
        model.addAttribute("expiryChartData", inventoryMonitorService.getExpiryChartData());
        model.addAttribute("nearExpiryAlerts", reportsAlertService.getNearExpiryAlerts());
        model.addAttribute("activityNotifications", notificationService.getRecentNotifications(5));
        return "admin-dashboard";
    }

    @GetMapping("/admin/storage")
    public String adminStorage(Model model) {
        AdminDashboardStats stats = adminDashboardService.getDashboardStats();
        model.addAttribute("stats", stats);
        model.addAttribute("locations", storageLocationService.getAllLocations());
        model.addAttribute("locationCount", storageLocationService.countLocations());
        if (!model.containsAttribute("locationRequest")) {
            model.addAttribute("locationRequest", new StorageLocationRequest());
        }
        return "admin-storage";
    }

    @PostMapping("/admin/storage/add")
    public String addStorageLocation(@ModelAttribute("locationRequest") StorageLocationRequest request,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            String actor = actorName(principal);
            request.setStaffId(staffService.getStaffIdByUsername(actor));
            storageLocationService.addLocation(request);
            notificationService.record(
                    "Storage Configuration",
                    "INSERT",
                    "Created storage location: " + safeLabel(request.getDescription(), "new location"),
                    actor
            );
            redirectAttributes.addFlashAttribute("successMessage", "Storage location created successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("locationRequest", request);
        }

        return "redirect:/admin/storage";
    }

    @PostMapping("/admin/storage/{id}/update")
    public String updateStorageLocation(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                        @ModelAttribute("locationRequest") StorageLocationRequest request,
                                        Principal principal,
                                        RedirectAttributes redirectAttributes) {
        try {
            String actor = actorName(principal);
            request.setStaffId(staffService.getStaffIdByUsername(actor));
            storageLocationService.updateLocation(id, request);
            notificationService.record(
                    "Storage Configuration",
                    "UPDATE",
                    "Updated storage location ID " + id,
                    actor
            );
            redirectAttributes.addFlashAttribute("successMessage", "Storage location updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/storage";
    }

    @PostMapping("/admin/storage/{id}/delete")
    public String archiveStorageLocation(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                         Principal principal,
                                         RedirectAttributes redirectAttributes) {
        try {
            storageLocationService.archiveLocation(id);
            notificationService.record(
                    "Storage Configuration",
                    "ARCHIVE",
                    "Archived storage location ID " + id,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Storage location archived successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/storage";
    }

    @PostMapping("/admin/storage/{id}/restore")
    public String restoreStorageLocation(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                         Principal principal,
                                         RedirectAttributes redirectAttributes) {
        try {
            storageLocationService.restoreLocation(id);
            notificationService.record(
                    "Storage Configuration",
                    "RESTORE",
                    "Restored storage location ID " + id,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Storage location restored successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/storage";
    }

    @GetMapping("/admin/inventory")
    public String adminInventory(Model model) {
        AdminDashboardStats stats = adminDashboardService.getDashboardStats();
        model.addAttribute("stats", stats);
        model.addAttribute("inventorySummary", inventoryMonitorService.getInventorySummary());
        model.addAttribute("componentStatusList", inventoryMonitorService.getComponentStatusSummary());
        model.addAttribute("expiryChartData", inventoryMonitorService.getExpiryChartData());
        return "admin-inventory";
    }

    @GetMapping("/admin/reports")
    public String adminReports(Model model) {
        AdminDashboardStats stats = adminDashboardService.getDashboardStats();
        model.addAttribute("stats", stats);
        model.addAttribute("reportsSummary", reportsAlertService.getReportsSummary());
        model.addAttribute("availableStockList", reportsAlertService.getAvailableStockByType());
        model.addAttribute("nearExpiryAlerts", reportsAlertService.getNearExpiryAlerts());
        model.addAttribute("staffRoleTotals", reportsAlertService.getStaffTotalsByRole());
        model.addAttribute("activityNotifications", notificationService.getRecentNotifications(30));
        return "admin-reports";
    }

    @GetMapping("/admin/reports/download")
    public ResponseEntity<byte[]> downloadAdminReport(@RequestParam(value = "type", required = false) String type,
                                                      @RequestParam(value = "format", required = false) String format) {
        String normalizedType = normalizeReportType(type);
        String normalizedFormat = normalizeReportFormat(format);
        ReportExport report = buildReportExport(normalizedType);
        byte[] body = renderReport(report, normalizedFormat);
        MediaType responseType = MediaType.parseMediaType(contentType(normalizedFormat));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + normalizedType + "-report." + fileExtension(normalizedFormat) + "\"")
                .contentType(responseType)
                .body(body);
    }

    @GetMapping("/admin/audit")
    public String adminAuditTrail(@RequestParam(value = "search", required = false) String search,
                                  @RequestParam(value = "tableName", required = false) String tableName,
                                  @RequestParam(value = "operationType", required = false) String operationType,
                                  @RequestParam(value = "actionType", required = false) String actionType,
                                  @RequestParam(value = "role", required = false) String role,
                                  @RequestParam(value = "sortBy", required = false) String sortBy,
                                  @RequestParam(value = "limit", required = false) Integer limit,
                                  Model model) {
        String normalizedSort = auditTrailService.normalizeSort(sortBy);
        int normalizedLimit = auditTrailService.normalizeLimit(limit);

        model.addAttribute("auditSummary", auditTrailService.getSummary());
        model.addAttribute("auditRecords", auditTrailService.getAuditRecords(
                search,
                tableName,
                operationType,
                actionType,
                role,
                normalizedSort,
                normalizedLimit
        ));
        model.addAttribute("auditTables", auditTrailService.getTableNames());
        model.addAttribute("auditOperations", auditTrailService.getOperationTypes());
        model.addAttribute("auditActions", auditTrailService.getActionTypes());
        model.addAttribute("auditRoles", auditTrailService.getRoles());
        model.addAttribute("auditSearch", search == null ? "" : search);
        model.addAttribute("auditTableName", tableName == null ? "" : tableName);
        model.addAttribute("auditOperationType", operationType == null ? "" : operationType);
        model.addAttribute("auditActionType", actionType == null ? "" : actionType);
        model.addAttribute("auditRole", role == null ? "" : role);
        model.addAttribute("auditSortBy", normalizedSort);
        model.addAttribute("auditLimit", normalizedLimit);
        return "admin-audit";
    }

    @GetMapping("/admin/audit/download")
    public ResponseEntity<byte[]> downloadAuditTrail(@RequestParam(value = "search", required = false) String search,
                                                     @RequestParam(value = "tableName", required = false) String tableName,
                                                     @RequestParam(value = "operationType", required = false) String operationType,
                                                     @RequestParam(value = "actionType", required = false) String actionType,
                                                     @RequestParam(value = "role", required = false) String role,
                                                     @RequestParam(value = "sortBy", required = false) String sortBy,
                                                     @RequestParam(value = "format", required = false) String format) {
        String normalizedFormat = normalizeReportFormat(format);
        ReportExport report = auditTrailExport(auditTrailService.getAuditRecords(
                search,
                tableName,
                operationType,
                actionType,
                role,
                auditTrailService.normalizeSort(sortBy),
                500
        ));
        byte[] body = renderReport(report, normalizedFormat);
        MediaType responseType = MediaType.parseMediaType(contentType(normalizedFormat));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-trail." + fileExtension(normalizedFormat) + "\"")
                .contentType(responseType)
                .body(body);
    }

    @GetMapping("/admin/deferral-rules")
    public String adminDeferralRules(Model model) {
        AdminDashboardStats stats = adminDashboardService.getDashboardStats();
        model.addAttribute("stats", stats);
        model.addAttribute("rules", deferralRuleService.getAllRules());
        model.addAttribute("ruleCount", deferralRuleService.countRules());
        if (!model.containsAttribute("ruleRequest")) {
            model.addAttribute("ruleRequest", new DeferralRuleRequest());
        }
        return "admin-deferral-rules";
    }

    @PostMapping("/admin/deferral-rules/add")
    public String addDeferralRule(@ModelAttribute("ruleRequest") DeferralRuleRequest request,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            String actor = actorName(principal);
            request.setStaffId(staffService.getStaffIdByUsername(actor));
            deferralRuleService.addRule(request);
            notificationService.record(
                    "Deferral Rules",
                    "INSERT",
                    "Created deferral rule: " + safeLabel(request.getDescription(), "new rule"),
                    actor
            );
            redirectAttributes.addFlashAttribute("successMessage", "Deferral rule created successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("ruleRequest", request);
        }

        return "redirect:/admin/deferral-rules";
    }

    @PostMapping("/admin/deferral-rules/{id}/update")
    public String updateDeferralRule(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                     @ModelAttribute("ruleRequest") DeferralRuleRequest request,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            String actor = actorName(principal);
            request.setStaffId(staffService.getStaffIdByUsername(actor));
            deferralRuleService.updateRule(id, request);
            notificationService.record(
                    "Deferral Rules",
                    "UPDATE",
                    "Updated deferral rule ID " + id,
                    actor
            );
            redirectAttributes.addFlashAttribute("successMessage", "Deferral rule updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/deferral-rules";
    }

    @PostMapping("/admin/deferral-rules/{id}/delete")
    public String archiveDeferralRule(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            deferralRuleService.archiveRule(id);
            notificationService.record(
                    "Deferral Rules",
                    "ARCHIVE",
                    "Archived deferral rule ID " + id,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Deferral rule archived successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/deferral-rules";
    }

    @PostMapping("/admin/deferral-rules/{id}/restore")
    public String restoreDeferralRule(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            deferralRuleService.restoreRule(id);
            notificationService.record(
                    "Deferral Rules",
                    "RESTORE",
                    "Restored deferral rule ID " + id,
                    actorName(principal)
            );
            redirectAttributes.addFlashAttribute("successMessage", "Deferral rule restored successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/deferral-rules";
    }

    @GetMapping("/medical/dashboard")
    public String medicalDashboard(Model model) {
        model.addAttribute("medicalSummary", medicalWorkflowService.getDashboardSummary());
        model.addAttribute("recentDonations", medicalWorkflowService.getDonationSessions().stream().limit(5).toList());
        model.addAttribute("safeComponents", medicalWorkflowService.getTransfusionReadyComponents().stream().limit(5).toList());
        return "medical-dashboard";
    }

    @GetMapping(value = "/medical/dashboard", params = "download")
    public ResponseEntity<byte[]> downloadMedicalDashboardReport(@RequestParam(value = "type", required = false) String type,
                                                                 @RequestParam(value = "format", required = false) String format) {
        String normalizedType = normalizeMedicalReportType(type);
        String normalizedFormat = normalizeReportFormat(format);
        ReportExport report = buildMedicalReportExport(normalizedType);
        byte[] body = renderReport(report, normalizedFormat);
        MediaType responseType = MediaType.parseMediaType(contentType(normalizedFormat));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"medical-dashboard-" + normalizedType + "." + fileExtension(normalizedFormat) + "\"")
                .contentType(responseType)
                .body(body);
    }

    @GetMapping("/lab/dashboard")
    public String labDashboard(Model model) {
        List<LabTestQueueDto> pendingTests = labWorkflowService.getPendingTests();
        List<LabTestQueueDto> labTests = labWorkflowService.getTestRecords();
        List<LabTestQueueDto> recentLabTests = labTests.stream().limit(5).toList();
        List<LabTestQueueDto> recentGraphSample = labTests.stream().limit(12).toList();
        List<DashboardChartSegmentDto> pendingQueueGraph = buildLabDashboardSegments(
                pendingTests,
                LabTestQueueDto::getBloodGroup,
                "Unknown group"
        );
        List<DashboardChartSegmentDto> recentTtiGraph = buildLabDashboardSegments(
                recentGraphSample,
                LabTestQueueDto::getTtiScreening,
                "Unknown"
        );
        List<DashboardChartSegmentDto> recentMatchGraph = buildLabDashboardSegments(
                recentGraphSample,
                LabTestQueueDto::getBloodTypeMatch,
                "Unknown"
        );
        List<DashboardChartSegmentDto> recentFinalGraph = buildLabDashboardSegments(
                recentGraphSample,
                LabTestQueueDto::getFinalStatus,
                "Unknown"
        );

        model.addAttribute("labSummary", labWorkflowService.getDashboardSummary());
        model.addAttribute("pendingTests", pendingTests.stream().limit(5).toList());
        model.addAttribute("pendingTrendTests", pendingTests);
        model.addAttribute("recentLabTests", recentLabTests);
        model.addAttribute("recentLabTrendTests", recentGraphSample);
        model.addAttribute("pendingQueueTotal", pendingTests.size());
        model.addAttribute("pendingQueueGraph", pendingQueueGraph);
        model.addAttribute("pendingQueueGraphMax", maxChartSegmentValue(pendingQueueGraph));
        model.addAttribute("recentLabGraphTotal", recentGraphSample.size());
        model.addAttribute("recentLabGraphMax", Math.max(1, recentGraphSample.size()));
        model.addAttribute("recentTtiGraph", recentTtiGraph);
        model.addAttribute("recentMatchGraph", recentMatchGraph);
        model.addAttribute("recentFinalGraph", recentFinalGraph);
        return "lab-dashboard";
    }

    private List<DashboardChartSegmentDto> buildLabDashboardSegments(
            List<LabTestQueueDto> rows,
            Function<LabTestQueueDto, String> labelExtractor,
            String fallbackLabel) {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (LabTestQueueDto row : rows) {
            String label = chartLabel(labelExtractor.apply(row), fallbackLabel);
            counts.merge(label, 1L, Long::sum);
        }

        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int byValue = Long.compare(right.getValue(), left.getValue());
                    return byValue != 0 ? byValue : left.getKey().compareToIgnoreCase(right.getKey());
                })
                .map(entry -> new DashboardChartSegmentDto(
                        entry.getKey(),
                        entry.getValue(),
                        chartTone(entry.getKey())
                ))
                .toList();
    }

    private long maxChartSegmentValue(List<DashboardChartSegmentDto> segments) {
        return segments.stream()
                .mapToLong(DashboardChartSegmentDto::getValue)
                .max()
                .orElse(1L);
    }

    private String chartLabel(String value, String fallbackLabel) {
        if (value == null || value.isBlank()) {
            return fallbackLabel;
        }

        return value.trim().replace('_', ' ');
    }

    private String chartTone(String label) {
        return LAB_CHART_TONES.getOrDefault(label.toUpperCase(Locale.ROOT), "neutral");
    }

    private String normalizeReportType(String type) {
        if (type == null) {
            return "summary";
        }

        return switch (type) {
            case "summary", "available-stock", "staff-roles", "near-expiry", "activity" -> type;
            default -> "summary";
        };
    }

    private String normalizeMedicalReportType(String type) {
        if (type == null) {
            return "summary";
        }

        return switch (type) {
            case "summary", "collections", "ready-components" -> type;
            default -> "summary";
        };
    }

    private String normalizeReportFormat(String format) {
        if (format == null) {
            return "csv";
        }

        return switch (format) {
            case "csv", "html", "pdf", "excel" -> format;
            default -> "csv";
        };
    }

    private ReportExport buildReportExport(String type) {
        return switch (type) {
            case "available-stock" -> availableStockExport(reportsAlertService.getAvailableStockByType());
            case "staff-roles" -> staffRolesExport(reportsAlertService.getStaffTotalsByRole());
            case "near-expiry" -> nearExpiryExport(reportsAlertService.getNearExpiryAlerts());
            case "activity" -> activityNotificationsExport(notificationService.getRecentNotifications(100));
            default -> summaryExport(reportsAlertService.getReportsSummary());
        };
    }

    private ReportExport buildMedicalReportExport(String type) {
        return switch (type) {
            case "collections" -> medicalCollectionsExport(medicalWorkflowService.getDonationSessions());
            case "ready-components" -> medicalReadyComponentsExport(medicalWorkflowService.getTransfusionReadyComponents());
            default -> medicalSummaryExport(
                    medicalWorkflowService.getDashboardSummary(),
                    medicalWorkflowService.getDonationSessions().size(),
                    medicalWorkflowService.getTransfusionReadyComponents().size()
            );
        };
    }

    private byte[] renderReport(ReportExport report, String format) {
        return switch (format) {
            case "html" -> utf8Bytes(reportHtml(report));
            case "pdf" -> reportPdf(report);
            case "excel" -> utf8Bytes(reportExcel(report));
            default -> utf8Bytes(reportCsv(report));
        };
    }

    private ReportExport summaryExport(ReportsSummaryDto summary) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("Total Staff", summary.getTotalStaff()));
        rows.add(row("Total Donors", summary.getTotalDonors()));
        rows.add(row("Total Donations", summary.getTotalDonations()));
        rows.add(row("Total Components", summary.getTotalComponents()));
        rows.add(row("Available Components", summary.getAvailableComponents()));
        rows.add(row("Near Expiry Components", summary.getNearExpiryComponents()));
        return new ReportExport("Reports Summary", List.of("Metric", "Value"), rows);
    }

    private ReportExport medicalSummaryExport(MedicalDashboardSummaryDto summary,
                                              int collectionSessions,
                                              int readyComponents) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("Eligible Donors", summary.getEligibleDonors()));
        rows.add(row("Deferred Donors", summary.getDeferredDonors()));
        rows.add(row("Today Donations", summary.getTodayDonations()));
        rows.add(row("Quarantined Units", summary.getQuarantinedComponents()));
        rows.add(row("Collection Sessions", collectionSessions));
        rows.add(row("Ready Matches", readyComponents));
        rows.add(row("Available Components", summary.getAvailableComponents()));
        rows.add(row("Transfusion Events", summary.getTransfusionEvents()));
        return new ReportExport("Medical Dashboard Summary", List.of("Metric", "Value"), rows);
    }

    private ReportExport availableStockExport(List<AvailableStockDto> rows) {
        return new ReportExport(
                "Available Stock by Type",
                List.of("Component Type", "Total Available"),
                rows.stream()
                        .map(row -> row(row.getComponentType(), row.getTotalAvailable()))
                        .toList()
        );
    }

    private ReportExport staffRolesExport(List<StaffRoleTotalDto> rows) {
        return new ReportExport(
                "Staff Totals by Role",
                List.of("Staff Type", "Total Staff"),
                rows.stream()
                        .map(row -> row(row.getStaffType(), row.getTotalStaff()))
                        .toList()
        );
    }

    private ReportExport nearExpiryExport(List<NearExpiryComponentDto> rows) {
        return new ReportExport(
                "Near Expiry Alerts",
                List.of("Component ID", "Type", "Status", "Expiry Time", "Location ID"),
                rows.stream()
                        .map(row -> row(
                                row.getComponentId(),
                                row.getComponentType(),
                                row.getStatus(),
                                formatTimestamp(row.getExpiryTimestamp()),
                                row.getLocationId()
                        ))
                        .toList()
        );
    }

    private ReportExport activityNotificationsExport(List<SystemNotificationDto> rows) {
        return new ReportExport(
                "System Activity Notifications",
                List.of("Created At", "Module", "Action", "Staff Name", "Username", "Source IP", "Message"),
                rows.stream()
                        .map(row -> row(
                                formatTimestamp(row.getCreatedAt()),
                                row.getModuleName(),
                                row.getActionType(),
                                row.getActorFullName(),
                                row.getActorUsername(),
                                row.getSourceIp(),
                                row.getMessage()
                        ))
                        .toList()
        );
    }

    private ReportExport auditTrailExport(List<AuditTrailDto> rows) {
        return new ReportExport(
                "Audit Trail",
                List.of("Time UTC", "Actor", "Role", "Category", "Operation", "Action", "Object", "Workflow Phase", "Request", "Component", "Donation", "Location", "Device", "Source IP", "Session Hash", "Row PK", "Integrity Hash", "Process Context"),
                rows.stream()
                        .map(row -> row(
                                row.getEventTimestampUtc(),
                                row.getActorLabel(),
                                row.getRole(),
                                row.getEventCategory(),
                                row.getOperationType(),
                                row.getActionType(),
                                row.getTableName(),
                                row.getWorkflowPhase(),
                                row.getRequestPath(),
                                row.getComponentId(),
                                row.getDonationId(),
                                row.getLocation(),
                                row.getDeviceId(),
                                row.getSourceIp(),
                                row.getSessionIdHash(),
                                row.getRowPk(),
                                row.getIntegrityHash(),
                                row.getProcessContext()
                        ))
                        .toList()
        );
    }

    private ReportExport medicalCollectionsExport(List<MedicalDonationDto> rows) {
        return new ReportExport(
                "Medical Collection Sessions",
                List.of("Donation ID", "Collection Time", "Donor", "Blood Group", "Components", "Status", "Storage", "Staff"),
                rows.stream()
                        .map(row -> row(
                                row.getDonationId(),
                                formatTimestamp(row.getCollectionTimestamp()),
                                row.getDonorName(),
                                row.getBloodGroup(),
                                row.getComponentCount(),
                                row.getComponentStatuses(),
                                row.getLocationDescription(),
                                row.getStaffName()
                        ))
                        .toList()
        );
    }

    private ReportExport medicalReadyComponentsExport(List<MedicalComponentDto> rows) {
        return new ReportExport(
                "Medical Ready Components",
                List.of("Component ID", "Type", "Donor", "Blood Group", "Status", "Expiry Time", "Storage", "Match"),
                rows.stream()
                        .map(row -> row(
                                row.getComponentId(),
                                row.getComponentType(),
                                row.getDonorName(),
                                row.getDonorBloodGroup(),
                                row.getStatus(),
                                formatTimestamp(row.getExpiryTimestamp()),
                                row.getLocationDescription(),
                                row.getCompatibilityNote()
                        ))
                        .toList()
        );
    }

    private String reportCsv(ReportExport report) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, report.headers().toArray());
        report.rows().forEach(row -> appendCsvRow(csv, row.toArray()));
        return csv.toString();
    }

    private String reportHtml(ReportExport report) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>%s</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 24px; color: #172033; }
                        h1 { font-size: 22px; margin-bottom: 16px; }
                        table { width: 100%%; border-collapse: collapse; }
                        th, td { border: 1px solid #d9e2ef; padding: 8px 10px; text-align: left; font-size: 12px; }
                        th { background: #eef5ff; }
                    </style>
                </head>
                <body>
                <h1>%s</h1>
                %s
                </body>
                </html>
                """.formatted(escapeHtml(report.title()), escapeHtml(report.title()), reportTableHtml(report));
    }

    private String reportExcel(ReportExport report) {
        return """
                <html xmlns:o="urn:schemas-microsoft-com:office:office"
                      xmlns:x="urn:schemas-microsoft-com:office:excel"
                      xmlns="http://www.w3.org/TR/REC-html40">
                <head><meta charset="UTF-8"></head>
                <body><h1>%s</h1>%s</body>
                </html>
                """.formatted(escapeHtml(report.title()), reportTableHtml(report));
    }

    private String reportTableHtml(ReportExport report) {
        StringBuilder html = new StringBuilder("<table><thead><tr>");
        report.headers().forEach(header -> html.append("<th>").append(escapeHtml(header)).append("</th>"));
        html.append("</tr></thead><tbody>");
        report.rows().forEach(row -> {
            html.append("<tr>");
            row.forEach(value -> html.append("<td>").append(escapeHtml(value)).append("</td>"));
            html.append("</tr>");
        });
        html.append("</tbody></table>");
        return html.toString();
    }

    private byte[] reportPdf(ReportExport report) {
        List<String> lines = new ArrayList<>();
        lines.add(report.title());
        lines.add(String.join(" | ", report.headers()));
        report.rows().stream()
                .map(row -> String.join(" | ", row))
                .limit(34)
                .forEach(lines::add);

        StringBuilder stream = new StringBuilder("BT\n/F1 11 Tf\n40 555 Td\n");
        for (String line : lines) {
            stream.append("(").append(escapePdfText(truncate(line, 118))).append(") Tj\n0 -15 Td\n");
        }
        stream.append("ET\n");

        return buildPdf(stream.toString());
    }

    private byte[] buildPdf(String contentStream) {
        byte[] streamBytes = contentStream.getBytes(StandardCharsets.ISO_8859_1);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + streamBytes.length + " >>\nstream\n" + contentStream + "endstream"
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        writePdf(output, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(output.size());
            writePdf(output, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }

        int xrefOffset = output.size();
        writePdf(output, "xref\n0 " + (objects.size() + 1) + "\n");
        writePdf(output, "0000000000 65535 f \n");
        offsets.forEach(offset -> writePdf(output, "%010d 00000 n \n".formatted(offset)));
        writePdf(output, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF");
        return output.toByteArray();
    }

    private byte[] utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private void writePdf(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private String contentType(String format) {
        return switch (format) {
            case "html" -> "text/html; charset=UTF-8";
            case "pdf" -> "application/pdf";
            case "excel" -> "application/vnd.ms-excel; charset=UTF-8";
            default -> "text/csv; charset=UTF-8";
        };
    }

    private String fileExtension(String format) {
        return switch (format) {
            case "html" -> "html";
            case "pdf" -> "pdf";
            case "excel" -> "xls";
            default -> "csv";
        };
    }

    private List<String> row(Object... values) {
        return Arrays.stream(values)
                .map(value -> value == null ? "" : value.toString())
                .toList();
    }

    private void appendCsvRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }

            csv.append(escapeCsv(values[i]));
        }

        csv.append("\r\n");
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }

        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }

        return text;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapePdfText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength - 3) + "...";
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }

        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(timestamp);
    }

    private String actorName(Principal principal) {
        if (principal == null) {
            return "system";
        }

        String name = principal.getName();
        return name == null || name.isBlank() ? "system" : name;
    }

    private String safeLabel(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }

        return fallback == null || fallback.isBlank() ? "record" : fallback.trim();
    }

    private record ReportExport(String title, List<String> headers, List<List<String>> rows) {
    }
}
