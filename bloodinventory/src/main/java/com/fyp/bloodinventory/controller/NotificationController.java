package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.service.SystemNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URI;
import java.net.URISyntaxException;

@Controller
public class NotificationController {

    private final SystemNotificationService notificationService;

    public NotificationController(SystemNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/notifications/read-all")
    public String markAllNotificationsRead(HttpServletRequest request) {
        notificationService.markAllAsRead();
        return "redirect:" + safeRedirectPath(request);
    }

    private String safeRedirectPath(HttpServletRequest request) {
        String fallback = "/dashboard";
        String referer = request.getHeader("Referer");

        if (referer == null || referer.isBlank()) {
            return fallback;
        }

        try {
            URI uri = new URI(referer);
            if (uri.isAbsolute() && !isSameOrigin(uri, request)) {
                return fallback;
            }

            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                return fallback;
            }

            String query = uri.getRawQuery();
            return query == null ? path : path + "?" + query;
        } catch (URISyntaxException ex) {
            return fallback;
        }
    }

    private boolean isSameOrigin(URI uri, HttpServletRequest request) {
        int requestPort = request.getServerPort();
        int uriPort = uri.getPort();
        int normalizedUriPort = uriPort == -1 ? defaultPort(uri.getScheme()) : uriPort;

        return request.getScheme().equalsIgnoreCase(uri.getScheme())
                && request.getServerName().equalsIgnoreCase(uri.getHost())
                && requestPort == normalizedUriPort;
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
