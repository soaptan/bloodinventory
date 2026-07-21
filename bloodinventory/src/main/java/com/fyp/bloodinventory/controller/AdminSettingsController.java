package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.BackupHistoryDto;
import com.fyp.bloodinventory.dto.BackupSettingsRequest;
import com.fyp.bloodinventory.dto.LanguageSettingsRequest;
import com.fyp.bloodinventory.dto.SecuritySettingsRequest;
import com.fyp.bloodinventory.dto.SystemUiSettingsRequest;
import com.fyp.bloodinventory.service.SystemNotificationService;
import com.fyp.bloodinventory.service.SystemSettingsService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Objects;

@Controller
public class AdminSettingsController {

    private final SystemSettingsService settingsService;
    private final SystemNotificationService notificationService;

    public AdminSettingsController(SystemSettingsService settingsService,
                                   SystemNotificationService notificationService) {
        this.settingsService = settingsService;
        this.notificationService = notificationService;
    }

    @GetMapping("/admin/settings")
    public String settingsPage(Model model) {
        List<BackupHistoryDto> backupHistory = settingsService.getRecentBackups();
        List<BackupHistoryDto> recoverableBackups = backupHistory.stream()
                .filter(this::isSuccessfulBackup)
                .toList();

        model.addAttribute("uiSettings", settingsService.getUiSettings());
        model.addAttribute("languageSettings", settingsService.getLanguageSettings());
        model.addAttribute("languageOptions", settingsService.getLanguageOptions());
        model.addAttribute("backupSettings", settingsService.getBackupSettings());
        model.addAttribute("securitySettings", settingsService.getSecuritySettings());
        model.addAttribute("backupHistory", backupHistory);
        model.addAttribute("recoverableBackups", recoverableBackups);
        model.addAttribute("latestSuccessfulBackup", recoverableBackups.isEmpty() ? null : recoverableBackups.get(0));
        return "admin-settings";
    }

    @PostMapping("/admin/settings/ui")
    public String updateUi(@ModelAttribute("uiSettings") SystemUiSettingsRequest request,
                           Principal principal,
                           RedirectAttributes redirectAttributes) {
        try {
            settingsService.updateUiSettings(request);
            record("Settings", "UPDATE", "Updated interface font size and accent color.", principal);
            redirectAttributes.addFlashAttribute("successMessage", "Interface settings saved and applied.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/settings#interface";
    }

    @PostMapping("/admin/settings/language")
    public String updateLanguage(@ModelAttribute("languageSettings") LanguageSettingsRequest request,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        settingsService.updateLanguageSettings(request);
        record("Settings", "UPDATE", "Updated default language preference.", principal);
        redirectAttributes.addFlashAttribute("successMessage", "Language setting saved.");
        return "redirect:/admin/settings";
    }

    @PostMapping("/admin/settings/backup")
    public String updateBackupSettings(@ModelAttribute("backupSettings") BackupSettingsRequest request,
                                       Principal principal,
                                       RedirectAttributes redirectAttributes) {
        settingsService.updateBackupSettings(request);
        record("Settings", "UPDATE", "Updated database backup schedule.", principal);
        redirectAttributes.addFlashAttribute("successMessage", "Backup schedule saved.");
        return "redirect:/admin/settings#backup";
    }

    @PostMapping("/admin/settings/backup/run")
    public String runManualBackup(Principal principal, RedirectAttributes redirectAttributes) {
        try {
            BackupHistoryDto backup = settingsService.runManualBackup(actorName(principal));
            record("Database Backup", "BACKUP", "Created manual backup: " + backup.getFileName(), principal);
            redirectAttributes.addFlashAttribute("successMessage", "Manual backup completed: " + backup.getFileName());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/settings#backup";
    }

    @PostMapping("/admin/settings/backup/recover")
    public String recoverBackup(@RequestParam("backupId") Long backupId,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            BackupHistoryDto recovery = settingsService.recoverBackup(backupId, actorName(principal));
            record("Database Backup", "RECOVERY", "Recovered database from backup: " + recovery.getFileName(), principal);
            redirectAttributes.addFlashAttribute("successMessage", "Recovery completed from backup: " + recovery.getFileName());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/settings#backup";
    }

    @GetMapping("/admin/settings/backup/{backupId}/download")
    public ResponseEntity<ByteArrayResource> downloadBackup(@PathVariable Long backupId) throws IOException {
        Path backupPath = settingsService.getBackupFilePath(backupId);
        if (!Files.exists(backupPath) || !Files.isRegularFile(backupPath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] backupBytes = Objects.requireNonNull(Files.readAllBytes(backupPath), "Backup bytes must not be null.");
        MediaType sqlMediaType = Objects.requireNonNull(MediaType.parseMediaType("application/sql"), "SQL media type must not be null.");
        ByteArrayResource resource = new ByteArrayResource(backupBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(backupPath.getFileName().toString())
                                .build()
                                .toString())
                .contentLength(resource.contentLength())
                .contentType(sqlMediaType)
                .body(resource);
    }

    @PostMapping("/admin/settings/security")
    public String updateSecurity(@ModelAttribute("securitySettings") SecuritySettingsRequest request,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        settingsService.updateSecuritySettings(request);
        record("Settings", "UPDATE", "Updated security and session policy.", principal);
        redirectAttributes.addFlashAttribute("successMessage", "Security settings saved.");
        return "redirect:/admin/settings";
    }

    @GetMapping(value = "/css/system-preferences.css", produces = "text/css")
    @ResponseBody
    public ResponseEntity<String> systemPreferenceCss() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(settingsService.getPreferenceCss());
    }

    private void record(String moduleName, String actionType, String message, Principal principal) {
        notificationService.record(moduleName, actionType, message, actorName(principal));
    }

    private @NonNull String actorName(Principal principal) {
        return principal == null ? "system" : Objects.requireNonNull(principal.getName(), "Principal name must not be null.");
    }

    private boolean isSuccessfulBackup(BackupHistoryDto backup) {
        return backup != null
                && "SUCCESS".equalsIgnoreCase(backup.getStatus())
                && backup.getFileName() != null
                && !backup.getFileName().isBlank();
    }
}
