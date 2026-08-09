package com.fyp.bloodinventory.config;

import com.fyp.bloodinventory.service.AuditEventService;
import com.fyp.bloodinventory.service.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class LoginSecurityFilter extends OncePerRequestFilter {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    private final LoginAttemptService loginAttemptService;
    private final AuditEventService auditEventService;

    public LoginSecurityFilter(LoginAttemptService loginAttemptService,
                               AuditEventService auditEventService) {
        this.loginAttemptService = loginAttemptService;
        this.auditEventService = auditEventService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String username = normalizeUsername(request.getParameter("username"));
        String password = request.getParameter("password");
        String sourceAddress = sourceAddress(request);

        if (!validUsername(username) || !validPasswordInput(password)) {
            boolean blocked = loginAttemptService.recordRejectedInput(sourceAddress);
            auditEventService.recordLoginFailure(request, username, "INVALID_LOGIN_INPUT");
            reject(response, blocked);
            return;
        }

        if (loginAttemptService.isBlocked(username, sourceAddress)) {
            auditEventService.recordLoginFailure(request, username, "LOGIN_THROTTLED");
            reject(response, true);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean validUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    private boolean validPasswordInput(String password) {
        return password != null
                && !password.isBlank()
                && password.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_LENGTH;
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sourceAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        return remoteAddress.length() <= 80 ? remoteAddress : remoteAddress.substring(0, 80);
    }

    private void reject(HttpServletResponse response, boolean blocked) throws IOException {
        if (blocked) {
            response.setHeader("Retry-After", String.valueOf(loginAttemptService.retryAfterSeconds()));
            response.sendRedirect("/login?throttled");
            return;
        }
        response.sendRedirect("/login?error");
    }
}
