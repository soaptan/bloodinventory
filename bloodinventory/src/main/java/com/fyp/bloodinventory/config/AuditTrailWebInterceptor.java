package com.fyp.bloodinventory.config;

import com.fyp.bloodinventory.service.AuditEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditTrailWebInterceptor implements HandlerInterceptor {

    private final AuditEventService auditEventService;

    public AuditTrailWebInterceptor(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                @Nullable Exception ex) {
        auditEventService.recordRequest(request, response.getStatus(), ex);
    }
}
