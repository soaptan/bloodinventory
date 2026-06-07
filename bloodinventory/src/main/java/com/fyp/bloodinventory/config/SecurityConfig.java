package com.fyp.bloodinventory.config;

import com.fyp.bloodinventory.service.CustomUserDetailsService;
import com.fyp.bloodinventory.service.DatabaseSessionControlService;
import com.fyp.bloodinventory.service.StaffModuleAccessService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final StaffModuleAccessService staffModuleAccessService;
    private final SystemNotificationService notificationService;
    private final DatabaseSessionControlService sessionControlService;
    private final DatabaseSessionControlFilter databaseSessionControlFilter;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService,
                          StaffModuleAccessService staffModuleAccessService,
                          SystemNotificationService notificationService,
                          DatabaseSessionControlService sessionControlService,
                          DatabaseSessionControlFilter databaseSessionControlFilter) {
        this.customUserDetailsService = customUserDetailsService;
        this.staffModuleAccessService = staffModuleAccessService;
        this.notificationService = notificationService;
        this.sessionControlService = sessionControlService;
        this.databaseSessionControlFilter = databaseSessionControlFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities();
            String username = authentication.getName();

            boolean sessionAllowed = sessionControlService.registerSuccessfulLogin(username, request);
            if (!sessionAllowed) {
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }

                SecurityContextHolder.clearContext();
                response.sendRedirect("/login?session=limit");
                return;
            }

            notificationService.record(
                    "Authentication",
                    "LOGIN",
                    "Logged in to the system.",
                    username,
                    request.getRemoteAddr()
            );

            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_BLOOD_ADMINISTRATOR"))) {
                response.sendRedirect("/admin/dashboard");
            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_MEDICAL_STAFF"))) {
                response.sendRedirect("/medical/dashboard");
            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_LAB_TECHNICIAN"))) {
                response.sendRedirect("/lab/dashboard");
            } else {
                response.sendRedirect("/login?error");
            }
        };
    }

    @Bean
    public AuthenticationFailureHandler customFailureHandler() {
        return (request, response, exception) -> {
            String attemptedPassword = request.getParameter("password");
            if (PasswordHashSupport.isBcryptHash(attemptedPassword)) {
                response.sendRedirect("/login?hashPassword");
                return;
            }

            if (exception instanceof DisabledException) {
                response.sendRedirect("/login?inactive");
                return;
            }

            if (exception instanceof LockedException) {
                response.sendRedirect("/login?locked");
                return;
            }

            response.sendRedirect("/login?error");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .userDetailsService(customUserDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/forgot-username", "/forgot-password", "/css/**", "/js/**", "/images/**", "/webjars/**", "/error").permitAll()
                        .requestMatchers("/access-denied", "/", "/dashboard").authenticated()
                        .requestMatchers("/profile/**", "/admin/staff/profile", "/admin/staff/profile/**").authenticated()
                        .requestMatchers("/api/chatbot/**", "/api/smart-search/**").authenticated()
                        .requestMatchers("/api/lab/**").hasRole("LAB_TECHNICIAN")
                        .requestMatchers("/admin/**", "/medical/**", "/lab/**").access(staffModuleAccessService)
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(customSuccessHandler())
                        .failureHandler(customFailureHandler())
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            sessionControlService.endSession(request.getSession(false), "LOGOUT");

                            if (authentication != null) {
                                notificationService.record(
                                        "Authentication",
                                        "LOGOUT",
                                        "Logged out from the system.",
                                        authentication.getName(),
                                        request.getRemoteAddr()
                                );
                            }

                            response.sendRedirect("/login?logout");
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/access-denied")
                )
                .sessionManagement(session -> session
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession())
                        .invalidSessionUrl("/login?session=expired")
                )
                .addFilterAfter(databaseSessionControlFilter, AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
