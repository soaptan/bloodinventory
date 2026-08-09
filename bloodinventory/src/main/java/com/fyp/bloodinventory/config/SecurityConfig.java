package com.fyp.bloodinventory.config;

import com.fyp.bloodinventory.service.AuditEventService;
import com.fyp.bloodinventory.service.CustomUserDetailsService;
import com.fyp.bloodinventory.service.DatabaseSessionControlService;
import com.fyp.bloodinventory.service.LoginAttemptService;
import com.fyp.bloodinventory.service.StaffModuleAccessService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final StaffModuleAccessService staffModuleAccessService;
    private final SystemNotificationService notificationService;
    private final DatabaseSessionControlService sessionControlService;
    private final DatabaseSessionControlFilter databaseSessionControlFilter;
    private final AuditEventService auditEventService;
    private final LoginAttemptService loginAttemptService;
    private final LoginSecurityFilter loginSecurityFilter;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService,
                          StaffModuleAccessService staffModuleAccessService,
                          SystemNotificationService notificationService,
                          DatabaseSessionControlService sessionControlService,
                          DatabaseSessionControlFilter databaseSessionControlFilter,
                          AuditEventService auditEventService,
                          LoginAttemptService loginAttemptService,
                          LoginSecurityFilter loginSecurityFilter) {
        this.customUserDetailsService = customUserDetailsService;
        this.staffModuleAccessService = staffModuleAccessService;
        this.notificationService = notificationService;
        this.sessionControlService = sessionControlService;
        this.databaseSessionControlFilter = databaseSessionControlFilter;
        this.auditEventService = auditEventService;
        this.loginAttemptService = loginAttemptService;
        this.loginSecurityFilter = loginSecurityFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities();
            String username = authentication.getName();

            loginAttemptService.recordSuccess(username, request.getRemoteAddr());

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
            auditEventService.recordLoginSuccess(request, username);

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
            String username = request.getParameter("username");
            auditEventService.recordLoginFailure(request, username, exception.getClass().getSimpleName());
            boolean blocked = loginAttemptService.recordFailure(username, request.getRemoteAddr());
            if (blocked) {
                response.setHeader("Retry-After", String.valueOf(loginAttemptService.retryAfterSeconds()));
                response.sendRedirect("/login?throttled");
            } else {
                response.sendRedirect("/login?error");
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .userDetailsService(customUserDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/forgot-username", "/forgot-password", "/reset-password", "/css/**", "/js/**", "/images/**", "/webjars/**", "/error").permitAll()
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
                                auditEventService.recordLogout(request, authentication.getName());
                            }

                            response.sendRedirect("/login?logout");
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            auditEventService.recordAccessDenied(request, accessDeniedException.getMessage());
                            response.sendRedirect("/access-denied");
                        })
                )
                .sessionManagement(session -> session
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession())
                        .invalidSessionUrl("/login?session=expired")
                )
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                                        + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                        + "font-src 'self' https://fonts.gstatic.com data:; "
                                        + "img-src 'self' data: blob:; "
                                        + "connect-src 'self'; object-src 'none'; base-uri 'self'; "
                                        + "form-action 'self'; frame-ancestors 'none'"
                        ))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"
                        ))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Permitted-Cross-Domain-Policies", "none"))
                )
                .addFilterBefore(loginSecurityFilter, UsernamePasswordAuthenticationFilter.class)
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

    @Bean
    public HttpFirewall strictHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(List.of("GET", "POST", "HEAD", "OPTIONS"));
        return firewall;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(HttpFirewall httpFirewall) {
        return (WebSecurity web) -> web.httpFirewall(httpFirewall);
    }
}
