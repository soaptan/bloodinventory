package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.entity.StaffRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class StaffModuleAccessService implements AuthorizationManager<RequestAuthorizationContext> {

    private final JdbcTemplate jdbcTemplate;

    public StaffModuleAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        return new AuthorizationDecision(canAccess(authentication.get(), context.getRequest()));
    }

    @Transactional(readOnly = true)
    public boolean canAccess(Authentication authentication, HttpServletRequest request) {
        if (!isSignedIn(authentication)) {
            return false;
        }

        String requestPath = normalizedRequestPath(request);
        return staffRoles(authentication).stream()
                .anyMatch(role -> canRoleAccessPath(role, requestPath));
    }

    @Transactional(readOnly = true)
    public List<String> getAccessibleModuleKeys(Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return List.of();
        }

        Set<String> moduleKeys = new LinkedHashSet<>();

        staffRoles(authentication).forEach(role ->
                moduleKeys.addAll(jdbcTemplate.queryForList(
                        "SELECT module_key FROM fn_staff_accessible_module_keys(?)",
                        String.class,
                        role.name()
                ))
        );

        return List.copyOf(moduleKeys);
    }

    private boolean canRoleAccessPath(StaffRole role, String requestPath) {
        Boolean allowed = jdbcTemplate.queryForObject(
                "SELECT fn_staff_can_access_path(?, ?)",
                Boolean.class,
                role.name(),
                requestPath
        );

        return Boolean.TRUE.equals(allowed);
    }

    private List<StaffRole> staffRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .map(this::toStaffRole)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<StaffRole> toStaffRole(String roleName) {
        try {
            return Optional.of(StaffRole.valueOf(roleName));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String normalizedRequestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }

        return requestUri;
    }
}
