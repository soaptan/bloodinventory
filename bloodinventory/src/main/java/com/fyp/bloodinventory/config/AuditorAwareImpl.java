package com.fyp.bloodinventory.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return auditor("SYSTEM");
        }

        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return auditor("SYSTEM");
        }

        return auditor(username);
    }

    @NonNull
    private Optional<String> auditor(String username) {
        return Objects.requireNonNull(Optional.of(username), "Auditor must not be null.");
    }
}
