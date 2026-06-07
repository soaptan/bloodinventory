package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.service.StaffModuleAccessService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import com.fyp.bloodinventory.service.SystemSettingsService;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;

@ControllerAdvice
public class DashboardNotificationAdvice {

    private final SystemNotificationService notificationService;
    private final StaffModuleAccessService staffModuleAccessService;
    private final SystemSettingsService systemSettingsService;

    public DashboardNotificationAdvice(SystemNotificationService notificationService,
                                       StaffModuleAccessService staffModuleAccessService,
                                       SystemSettingsService systemSettingsService) {
        this.notificationService = notificationService;
        this.staffModuleAccessService = staffModuleAccessService;
        this.systemSettingsService = systemSettingsService;
    }

    @ModelAttribute
    public void addNotificationAttributes(Model model, Principal principal, Authentication authentication) {
        model.addAttribute("currentLanguage", systemSettingsService.getLanguageSettings().getLanguageCode());

        if (principal == null) {
            model.addAttribute("recentNotifications", List.of());
            model.addAttribute("unreadNotificationCount", 0L);
            model.addAttribute("accessibleModules", List.of());
            return;
        }

        model.addAttribute("recentNotifications", notificationService.getRecentNotifications());
        model.addAttribute("unreadNotificationCount", notificationService.countUnreadNotifications());
        model.addAttribute("accessibleModules", staffModuleAccessService.getAccessibleModuleKeys(authentication));
    }
}
