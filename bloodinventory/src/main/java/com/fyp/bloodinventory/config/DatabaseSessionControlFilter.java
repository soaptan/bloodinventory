package com.fyp.bloodinventory.config;

import com.fyp.bloodinventory.service.DatabaseSessionControlService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DatabaseSessionControlFilter extends OncePerRequestFilter {

    private final DatabaseSessionControlService sessionControlService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public DatabaseSessionControlFilter(DatabaseSessionControlService sessionControlService) {
        this.sessionControlService = sessionControlService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);

        if (isSignedIn(authentication) && session != null) {
            boolean active = sessionControlService.validateAndTouch(authentication.getName(), session);

            if (!active) {
                logoutHandler.logout(request, response, authentication);
                response.sendRedirect(request.getContextPath() + "/login?session=expired");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
