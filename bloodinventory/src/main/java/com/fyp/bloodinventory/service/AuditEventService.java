package com.fyp.bloodinventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuditEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventService.class);
    private static final Set<String> SENSITIVE_PARAMETERS = Set.of(
            "password",
            "newpassword",
            "currentpassword",
            "confirmpassword",
            "token",
            "resettoken",
            "_csrf",
            "csrf",
            "secret",
            "credential",
            "otp"
    );

    private final DatabaseAuditContextService auditContextService;
    private final ObjectMapper objectMapper;

    public AuditEventService(DatabaseAuditContextService auditContextService,
                             ObjectMapper objectMapper) {
        this.auditContextService = auditContextService;
        this.objectMapper = objectMapper;
    }

    public void recordRequest(HttpServletRequest request, int responseStatus, Exception exception) {
        if (!isSignedIn(currentAuthentication()) || shouldSkipRequest(request) || !isDataChangingRequest(request)) {
            return;
        }

        RequestAuditEvent event = classify(request);
        Map<String, Object> context = baseRequestContext(request);
        context.put("status", responseStatus);
        context.put("result", exception == null && responseStatus < 400 ? "success" : "error");
        if (exception != null) {
            context.put("exception", exception.getClass().getSimpleName());
        }

        record(
                event.category(),
                event.operation(),
                event.action(),
                event.objectName(),
                event.rowPk(),
                event.workflowPhase(),
                request.getRequestURI(),
                request.getMethod(),
                context
        );
    }

    public void recordLoginSuccess(HttpServletRequest request, String username) {
        Map<String, Object> context = baseRequestContext(request);
        context.put("result", "success");
        context.put("username", username);
        record(
                "SECURITY",
                "LOGIN",
                "LOGIN_SUCCESS",
                "authentication_event",
                username,
                "Authentication",
                request.getRequestURI(),
                request.getMethod(),
                context
        );
    }

    public void recordLoginFailure(HttpServletRequest request, String attemptedUsername, String reason) {
        Map<String, Object> context = baseRequestContext(request);
        context.put("result", "failed");
        context.put("attempted_username", safeText(attemptedUsername));
        context.put("reason", safeText(reason));
        record(
                "SECURITY",
                "LOGIN",
                "LOGIN_FAILURE",
                "authentication_event",
                safeText(attemptedUsername),
                "Authentication",
                request.getRequestURI(),
                request.getMethod(),
                context
        );
    }

    public void recordPasswordRecoveryAttempt(HttpServletRequest request,
                                              String attemptedUsername,
                                              String reason,
                                              boolean successful) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("path", request.getRequestURI());
        context.put("method", request.getMethod());
        context.put("remote_address", request.getRemoteAddr());
        context.put("result", successful ? "success" : "failed");
        context.put("attempted_username", safeText(attemptedUsername));
        context.put("reason", safeText(reason));
        record(
                "SECURITY",
                "PASSWORD_RECOVERY",
                successful ? "PASSWORD_RECOVERY_VERIFIED" : "PASSWORD_RECOVERY_FAILURE",
                "authentication_event",
                safeText(attemptedUsername),
                "Authentication",
                request.getRequestURI(),
                request.getMethod(),
                context
        );
    }

    public void recordLogout(HttpServletRequest request, String username) {
        Map<String, Object> context = baseRequestContext(request);
        context.put("result", "success");
        context.put("username", username);
        record(
                "SECURITY",
                "LOGOUT",
                "LOGOUT",
                "authentication_event",
                username,
                "Authentication",
                request.getRequestURI(),
                request.getMethod(),
                context
        );
    }

    public void recordAccessDenied(HttpServletRequest request, String reason) {
        Map<String, Object> context = baseRequestContext(request);
        context.put("result", "denied");
        context.put("reason", safeText(reason));
        record(
                "SECURITY",
                "ACCESS_DENIED",
                "ACCESS_DENIED",
                objectName(request.getRequestURI()),
                request.getRequestURI(),
                "Authorization",
                request.getRequestURI(),
                request.getMethod(),
                context
        );
    }

    private void record(String category,
                        String operation,
                        String action,
                        String objectName,
                        String rowPk,
                        String workflowPhase,
                        String requestPath,
                        String httpMethod,
                        Map<String, Object> context) {
        try {
            String contextJson = toJson(context);
            auditContextService.executeWithCurrentContext(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT public.fn_record_audit_event(?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """)) {
                    statement.setString(1, category);
                    statement.setString(2, operation);
                    statement.setString(3, action);
                    statement.setString(4, objectName);
                    statement.setString(5, rowPk);
                    statement.setString(6, workflowPhase);
                    statement.setString(7, requestPath);
                    statement.setString(8, httpMethod);
                    statement.setString(9, contextJson);
                    statement.execute();
                    return null;
                }
            });
        } catch (RuntimeException ex) {
            LOGGER.warn("Audit event logging failed for {} {}", operation, action, ex);
        }
    }

    private RequestAuditEvent classify(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        if (isDownload(request)) {
            return new RequestAuditEvent("USER_ACTION", "DOWNLOAD", "DOWNLOAD", objectName(path), path, workflowPhase(path));
        }

        if (isSearch(request)) {
            return new RequestAuditEvent("VIEW", "SEARCH", "SEARCH", objectName(path), path, "Information Access");
        }

        if ("GET".equals(method)) {
            return new RequestAuditEvent("VIEW", "VIEW", "VIEW", objectName(path), path, workflowPhase(path));
        }

        return new RequestAuditEvent("USER_ACTION", "ACTION", inferAction(method, path), objectName(path), path, workflowPhase(path));
    }

    private boolean isDownload(HttpServletRequest request) {
        String path = request.getRequestURI().toLowerCase(Locale.ROOT);
        return path.contains("/download") || request.getParameterMap().containsKey("download");
    }

    private boolean isSearch(HttpServletRequest request) {
        String path = request.getRequestURI().toLowerCase(Locale.ROOT);
        return path.contains("smart-search")
                || request.getParameterMap().containsKey("search")
                || request.getParameterMap().containsKey("q");
    }

    private String inferAction(String method, String path) {
        String normalizedPath = path.toLowerCase(Locale.ROOT);
        if (normalizedPath.contains("/delete") || normalizedPath.contains("/archive")) {
            return "DELETE_REQUEST";
        }
        if (normalizedPath.contains("/restore")) {
            return "RESTORE";
        }
        if (normalizedPath.contains("/recover")) {
            return "RECOVERY";
        }
        if (normalizedPath.contains("/backup/run")) {
            return "BACKUP";
        }
        if (normalizedPath.contains("/add") || normalizedPath.contains("/create") || normalizedPath.contains("/register")) {
            return "CREATE_REQUEST";
        }
        if (normalizedPath.contains("/update") || normalizedPath.contains("/settings") || "PUT".equals(method) || "PATCH".equals(method)) {
            return "UPDATE_REQUEST";
        }
        if ("DELETE".equals(method)) {
            return "DELETE_REQUEST";
        }

        return method + "_REQUEST";
    }

    private String objectName(String path) {
        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (normalizedPath.startsWith("/admin/settings/backup")) {
            return "backup_event";
        }
        if (normalizedPath.startsWith("/admin/settings")) {
            return "settings_event";
        }
        if (normalizedPath.startsWith("/admin/audit")) {
            return "audit_event";
        }
        if (normalizedPath.startsWith("/admin/reports")) {
            return "report_event";
        }
        if (normalizedPath.startsWith("/admin/storage")) {
            return "storage_event";
        }
        if (normalizedPath.startsWith("/admin/deferral-rules")) {
            return "deferral_event";
        }
        if (normalizedPath.startsWith("/admin/staff")) {
            return "staff_event";
        }
        if (normalizedPath.startsWith("/admin")) {
            return "admin_event";
        }
        if (normalizedPath.startsWith("/medical")) {
            return "medical_event";
        }
        if (normalizedPath.startsWith("/lab")) {
            return "lab_event";
        }
        if (normalizedPath.contains("smart-search")) {
            return "search_event";
        }
        if (normalizedPath.startsWith("/api")) {
            return "api_event";
        }
        if (normalizedPath.startsWith("/profile")) {
            return "profile_event";
        }

        return "application_event";
    }

    private String workflowPhase(String path) {
        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (normalizedPath.startsWith("/admin/settings/backup")) {
            return "Backup and Recovery";
        }
        if (normalizedPath.startsWith("/admin/settings")) {
            return "System Settings";
        }
        if (normalizedPath.startsWith("/admin/audit")) {
            return "Audit Review";
        }
        if (normalizedPath.startsWith("/admin")) {
            return "Administration";
        }
        if (normalizedPath.startsWith("/medical")) {
            return "Medical Workflow";
        }
        if (normalizedPath.startsWith("/lab")) {
            return "Laboratory Workflow";
        }
        if (normalizedPath.startsWith("/api")) {
            return "API Workflow";
        }
        if (normalizedPath.startsWith("/profile")) {
            return "Staff Profile";
        }

        return "Application Activity";
    }

    private boolean shouldSkipRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }

        String normalizedPath = path.toLowerCase(Locale.ROOT);
        return normalizedPath.equals("/login")
                || normalizedPath.equals("/logout")
                || normalizedPath.equals("/error")
                || normalizedPath.equals("/access-denied")
                || normalizedPath.startsWith("/css/")
                || normalizedPath.startsWith("/js/")
                || normalizedPath.startsWith("/images/")
                || normalizedPath.startsWith("/webjars/")
                || normalizedPath.startsWith("/uploads/")
                || normalizedPath.startsWith("/actuator/")
                || normalizedPath.equals("/favicon.ico");
    }

    private boolean isDataChangingRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null) {
            return false;
        }

        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    private Map<String, Object> baseRequestContext(HttpServletRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("path", request.getRequestURI());
        context.put("method", request.getMethod());
        context.put("parameters", sanitizedParameters(request));
        context.put("remote_address", request.getRemoteAddr());
        return context;
    }

    private Map<String, Object> sanitizedParameters(HttpServletRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            if (SENSITIVE_PARAMETERS.contains(normalizedKey) || normalizedKey.contains("password") || normalizedKey.contains("token")) {
                parameters.put(key, "[redacted]");
            } else {
                parameters.put(key, Arrays.stream(values == null ? new String[0] : values)
                        .map(this::truncateValue)
                        .toList());
            }
        });
        return parameters;
    }

    private String toJson(Map<String, Object> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Unable to serialize audit event context.", ex);
            return "{}";
        }
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String truncateValue(String value) {
        if (value == null || value.length() <= 160) {
            return value;
        }

        return value.substring(0, 160);
    }

    private String safeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : truncateValue(normalized);
    }

    private record RequestAuditEvent(String category,
                                     String operation,
                                     String action,
                                     String objectName,
                                     String rowPk,
                                     String workflowPhase) {
    }
}
